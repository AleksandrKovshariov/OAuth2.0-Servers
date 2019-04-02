package database;

import resource.AccessType;
import resource.Access;
import resource.Resource;
import resource.ResourceServ;

import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Database implements Access<String, Resource> {
    private Connection connection;
    private String usernameAccess = "select * from user_access" +
            " WHERE username = ? and acc_path = ? and is_dir = ? and access_type like ?";

    private String userAccess = "select acc_path, is_dir, access_type from user_access" +
            " WHERE username = ? ";

    private String addAccess = "insert into Accesses(acc_path,  is_dir, username, access_type) values(?, ?, ?, ?)";

    private String deleteAccess = "delete from accesses " +
            " where acc_path = ? and is_dir = ?";



    public Database(Connection connection) {
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
                System.out.println(preparedStatement);
                ResultSet result = preparedStatement.executeQuery();
                return result.first();
            }

        }catch (SQLException e){
            System.err.println(e);
        }
        return false;
    }


    @Override
    public void addAccess(Resource resource) {
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(addAccess)){
                preparedStatement.setString(1,  ResourceServ.unixLikePath(resource.getPath().toString()));
                preparedStatement.setBoolean(2, resource.isDir());
                preparedStatement.setString(3, resource.getUsername());
                String access_type = Arrays.toString(resource.getAccessTypes())
                        .replaceAll("\\[|\\]| ", "");
                preparedStatement.setString(4, access_type);
                System.out.println(preparedStatement);
                preparedStatement.execute();
            }

        }catch (SQLException e){
            System.err.println(e);
        }

    }

    @Override
    public void deleteAccess(Resource resource) {

        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(deleteAccess)){
                String unixLikePath = ResourceServ.unixLikePath(resource.getPath().toString());
                preparedStatement.setString(1,unixLikePath);
                preparedStatement.setBoolean(2, resource.isDir());
                System.out.println(preparedStatement);
                preparedStatement.execute();
            }

        }catch (SQLException e){
            System.err.println(e);
        }

    }

    //refactor....
    @Override
    public List<Resource> getUserAccess(String name, String... params){
        List<Resource> list = new ArrayList<>();
        try{
            String userAccess = this.userAccess;
            for(String s : params){
                if(s.startsWith("access_type=")) {
                    String acc = s.substring(s.indexOf("=") + 1);
                    s = " access_type like " + "\'%" + acc + "%\'";
                }
                userAccess += " and " + s;
            }
            try(PreparedStatement preparedStatement = connection.prepareStatement(userAccess)){
                preparedStatement.setString(1, name);

                ResultSet resultSet = preparedStatement.executeQuery();
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
            System.err.println(e);
        }
        return list;
    }
}
