package database;

import resource.AccessType;
import resource.Access;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class Database implements Access<String, Path> {
    private Connection connection;
    private String usernameAccess = "select * from (" +
            " select u.username,  acc_path,  is_dir from users u join accesses USING(username)" +
            " union " +
            " select u.username, ac.acc_path, ac.is_dir from users u join groups g USING(group_name)" +
            " join accesses_has_groups a USING(group_name) " +
            " join accesses ac USING(access_id) " +
            ") alias " +
            "WHERE username = ? and acc_path = ? and is_dir = ?";

    public Database(Connection connection) {
        this.connection = connection;
    }

    @Override
    public boolean hasAccess(AccessType accessType, String username, Path path){
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(usernameAccess)){
                String unixLikePath = path.toString().replaceAll("\\\\", "/");
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
}
