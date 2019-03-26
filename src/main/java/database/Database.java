package database;

import resource.AccessType;
import resource.Access;

import java.sql.*;

public class Database implements Access<String, String> {
    private Connection connection;
    private String usernameAccess = "select * from (" +
            " select u.username,  acc_path,  is_dir from users u join accesses USING(username)" +
            " union " +
            " select u.username, ac.acc_path, ac.is_dir from users u join groups g USING(group_name)" +
            " join accesses_has_groups a USING(group_name) " +
            " join accesses ac USING(access_id) " +
            ") alias " +
            "WHERE username = ?";

    public Database(Connection connection) {
        this.connection = connection;
    }

    @Override
    public boolean hasAccess(AccessType accessType, String username, String path){
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(usernameAccess)){
                preparedStatement.setString(1, username);
                ResultSet result = preparedStatement.executeQuery();
                while (result.next()){
                    System.out.print(result.getString(1) + "\t");
                    System.out.println(result.getString(2));
                }
                return true;
            }

        }catch (SQLException e){
            System.err.println(e);
        }
        return false;
    }

    @Override
    public void addAccess(String name, String access) {

    }

    @Override
    public void deleteAccess(String name, String access) {

    }
}
