package database;

import resource.AccessType;
import resource.Access;

import javax.naming.OperationNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Database implements Access<String, Path> {
    private Connection connection;
    private String usernameAccess = "select * from user_access" +
            " WHERE username = ? and acc_path = ? and is_dir = ?";

    private String userAccess = "select acc_path from user_access" +
            " WHERE username = ?";

    public Database(Connection connection) {
        this.connection = connection;
    }

    @Override
    public boolean hasAccess(AccessType accessType, String username, Path path){
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(usernameAccess)){
                String unixLikePath = path.toString().replaceAll("\\\\", "/");
                System.out.println(unixLikePath);
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, unixLikePath);
                preparedStatement.setBoolean(3, Files.isDirectory(path));
                ResultSet result = preparedStatement.executeQuery();
                return result.first();
            }

        }catch (SQLException e){
            System.err.println(e);
        }
        return false;
    }

    @Override
    public void addAccess(String name, Path access) {

    }

    @Override
    public void deleteAccess(String name, Path access) {

    }

    @Override
    public List<Path> getUserAccess(String name){
        List<Path> list = new ArrayList<>();
        try{
            try(PreparedStatement preparedStatement = connection.prepareStatement(userAccess)){
                preparedStatement.setString(1, name);
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()){
                    list.add(Paths.get(resultSet.getString(1)));
                }

            }
        }catch (SQLException e){
            System.err.println(e);
        }
        return list;
    }
}
