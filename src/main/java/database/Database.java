package database;

import resource.AccessType;
import resource.Access;
import resource.Resource;
import resource.ResourceServ;
import utils.FineLogger;

import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database implements Access<String, Resource> {
    private Connection connection;
    private static final Logger logger = FineLogger.getLogger(Database.class.getName(), "logs/databaseLog.txt");
    private static final String usernameAccess = "select * from user_access" +
            " WHERE username = ? and acc_path = ? and is_dir = ? and access_type like ?";

    private static final String userAccess = "select acc_path, is_dir, access_type from user_access" +
            " WHERE username = ? ";

    private static final String addAccess =
            "insert into Accesses(acc_path,  is_dir, username, access_type) values(?, ?, ?, ?)";

    private static final String deleteAccess = "delete from accesses " +
            " where acc_path = ? and is_dir = ?";



    public Database(Connection connection) {
        Objects.requireNonNull(connection);
        this.connection = connection;
    }


    @Override
    public boolean hasAccess(Resource resource){
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(usernameAccess)){
                String unixLikePath = ResourceServ.unixLikePath(resource.getPath().toString());
                preparedStatement.setString(1, resource.getUsername());
                preparedStatement.setString(2, unixLikePath);
                preparedStatement.setBoolean(3, Files.isDirectory(resource.getPath()));

                preparedStatement.setString(4, Arrays.toString(resource.getAccessTypes())
                        .replaceAll("\\[|\\]", "%"));
                logger.finer(preparedStatement.toString());
                ResultSet result = preparedStatement.executeQuery();
                return result.first();
            }

        }catch (SQLException e){
            logger.log(Level.CONFIG, "Sqlex", e);
        }
        return false;
    }


    @Override
    public void addAccess(Resource resource) throws Exception {
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(addAccess)){
                preparedStatement.setString(1,  ResourceServ.unixLikePath(resource.getPath().toString()));
                preparedStatement.setBoolean(2, resource.isDir());
                preparedStatement.setString(3, resource.getUsername());

                String access_type = Arrays.toString(resource.getAccessTypes())
                        .replaceAll("\\[|\\]| ", "");
                preparedStatement.setString(4, access_type);
                logger.finer(preparedStatement.toString());
                preparedStatement.execute();
            }

        }catch (SQLIntegrityConstraintViolationException e){
            //Almost always means that user overriding file
            logger.log(Level.CONFIG, "Sqlex", e);
        }

    }

    @Override
    public void deleteAccess(Resource resource) throws Exception{

        try (PreparedStatement preparedStatement = connection.prepareStatement(deleteAccess)){
            String unixLikePath = ResourceServ.unixLikePath(resource.getPath().toString());
            preparedStatement.setString(1,unixLikePath);
            preparedStatement.setBoolean(2, resource.isDir());
            logger.finer(preparedStatement.toString());
            preparedStatement.execute();
        }

    }

    private static String modifyWithParams(Map<String, String> params, String query){
        System.out.println(params);
        for(String s : params.keySet()){
            switch (s){
                case "access_type":
                    String[] accesses = params.get(s).split(",");
                    StringBuilder sb = new StringBuilder();
                    for(String str : accesses)
                        sb.append(" and access_type like \'%").append(str).append( "%\' ");
                    s = sb.toString();
                    break;
                case "path":
                    String path = params.get(s);
                    s = " and acc_path like \'" + path
                            + "%\' and INSTR(SUBSTRING(acc_path, length('" + path + "') + 1), '/') = 0";
                    break;
                default:
                    s = " and " + s + " = " + params.get(s);
            }
            query +=  s;
        }
        return  query;
    }

    //refactor....
    @Override
    public List<Resource> getUserAccess(String name, Map<String, String> params){
        List<Resource> list = new ArrayList<>();
        try{
            String userAccessQuery = modifyWithParams(params, userAccess);

            try(PreparedStatement preparedStatement = connection.prepareStatement(userAccessQuery)){
                preparedStatement.setString(1, name);
                System.out.println(preparedStatement);
                ResultSet resultSet = preparedStatement.executeQuery();
                logger.finer(preparedStatement.toString());
                while (resultSet.next()){
                    Path path = Paths.get(resultSet.getString(1));
                    boolean isDir = resultSet.getBoolean(2);
                    String[] accessTypesStr = resultSet.getString(3).split(",");
                    AccessType[] accessTypes = new AccessType[accessTypesStr.length];
                    for (int i = 0; i < accessTypes.length; i++) {
                        accessTypes[i] = AccessType.valueOf(accessTypesStr[i]);
                    }
                    Resource resource = new Resource(isDir, path, name, accessTypes);
                    list.add(resource);
                }

            }
        }catch (SQLException e){
            logger.log(Level.CONFIG, "Sqlex", e);
        }
        return list;
    }
}
