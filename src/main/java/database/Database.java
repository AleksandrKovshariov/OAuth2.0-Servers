package database;

import resource.AccessType;
import resource.Access;
import resource.Resource;

import javax.naming.OperationNotSupportedException;
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
            " WHERE username = ? and acc_path = ? and is_dir = ?";

    private String userAccess = "select acc_path, is_dir, access_type from user_access" +
            " WHERE username = ?";

    public Database(Connection connection) {
        this.connection = connection;
    }

    private static String unixLikePath(Path path){
        return path.toString().replaceAll("\\\\", "/");
    }

    @Override
    public boolean hasAccess(Resource resource){
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(usernameAccess)){
                String unixLikePath = unixLikePath(resource.getPath());
                preparedStatement.setString(1, resource.getUsername());
                preparedStatement.setString(2, unixLikePath);
                preparedStatement.setBoolean(3, Files.isDirectory(resource.getPath()));
                ResultSet result = preparedStatement.executeQuery();
                return result.first();
            }

        }catch (SQLException e){
            System.err.println(e);
        }
        return false;
    }


    @Override
    public void addAccess(Resource access) {

    }

    @Override
    public void deleteAccess(Resource access) {

    }

    @Override
    public List<Resource> getUserAccess(String name){
        List<Resource> list = new ArrayList<>();
        try{
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

                    Resource resource = new Resource(isDir, path, null, accessTypes);
                    list.add(resource);
                }

            }
        }catch (SQLException e){
            System.err.println(e);
        }
        return list;
    }
}
