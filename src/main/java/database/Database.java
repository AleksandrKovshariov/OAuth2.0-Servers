package database;

import java.sql.*;

public class Database {
    private Connection connection;
    private String usernameAccess = "select * from (" +
            " select u.username,  acc_path,  is_dir from users u join accesses USING(username)" +
            " union " +
            " select u.username, ac.acc_path, ac.is_dir from users u join groups g USING(group_name)" +
            " join groups_accesses a USING(group_name) " +
            " join accesses ac USING(acc_path, is_dir) " +
            ") alias " +
            "WHERE username = ?";

    public Database(Connection connection) {
        this.connection = connection;
    }

    public boolean hasAccess(String username, String path){
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


}
