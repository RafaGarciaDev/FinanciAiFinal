package financiai.financiai.util;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexao {
    private static final String URL = "jdbc:mysql://financiai-financiaidatabase.g.aivencloud.com:21022/financiAi?sslMode=REQUIRED";
    private static final String USUARIO = "avnadmin";
    private static final String SENHA = "AVNS_rMPiPdqPv39yfgERwOD";

    public static Connection conectar() {
        try {
            return DriverManager.getConnection(URL, USUARIO, SENHA);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conectar ao banco de dadoss", e);
        }
    }
}
