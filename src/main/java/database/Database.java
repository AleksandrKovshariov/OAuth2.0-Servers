package database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private Connection connection;
    private String usernameAccess = "select * from ( " +
            "select u.username,  acc_path, acc_type from users u join accesses USING(username) " +
            "union" +
            " select u.username, ac.acc_path, ac.acc_type from users u join groups g on g.group_name = u.group_name " +
            " join groups_accesses a on a.groups_name = g.group_name " +
            " join accesses ac on ac.acc_path = a.acc_path and ac.acc_type = a.acc_type " +
            ") alias ";

    public Database(Connection connection) {
        this.connection = connection;
    }

    public boolean hasAccess(String username, String path){
        try {
            try (Statement statement = connection.createStatement()){
                ResultSet result = statement.executeQuery(
                        usernameAccess + "WHERE username =" + "'" + username + "'");

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
