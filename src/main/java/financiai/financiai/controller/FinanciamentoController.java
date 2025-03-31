package financiai.financiai.controller;

import financiai.financiai.model.Cliente;
import financiai.financiai.model.Financiamento;
import financiai.financiai.model.Imovel;
import financiai.financiai.model.Parcela;
import financiai.financiai.services.TipoAmortizacao;
import financiai.financiai.services.TipoImovel;
import financiai.financiai.dao.ClienteDAO;
import financiai.financiai.dao.DatabaseSetupDAO;
import financiai.financiai.dao.FinanciamentoDAO;
import financiai.financiai.dao.ImovelDAO;
import financiai.financiai.dao.ParcelasDAO;
import financiai.financiai.util.Conexao;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class FinanciamentoController {
    @FXML private TextField nomeClienteField;
    @FXML private TextField cpfClienteField;
    @FXML private TextField rendaClienteField;
    @FXML private TextField valorImovelField;
    @FXML private TextField valorEntradaField;
    @FXML private TextField taxaJurosField;
    @FXML private TextField prazoField;
    @FXML private ChoiceBox<String> tipoImovelBox;
    @FXML private ChoiceBox<String> tipoFinanciamentoBox;
    @FXML private Label resultadoLabel;

    private Stage primaryStage;
    private Financiamento financiamento;
    private Cliente cliente;
    private Imovel imovel;
    private List<Parcela> parcelas;

    @FXML
    public void initialize() {
        tipoImovelBox.getItems().addAll("Casa", "Apartamento");
        tipoFinanciamentoBox.getItems().addAll("SAC", "Price");
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    private void calcularFinanciamento() {
        try {
            // Obter dados do formulário
            String nome = nomeClienteField.getText();
            String cpf = cpfClienteField.getText();
            double renda = Double.parseDouble(rendaClienteField.getText().replace(",", "."));
            double valorImovel = Double.parseDouble(valorImovelField.getText().replace(",", "."));
            double valorEntrada = Double.parseDouble(valorEntradaField.getText().replace(",", "."));
            double taxaJurosMensal = Double.parseDouble(taxaJurosField.getText().replace(",", ".")) / 100;
            double taxaJuros = Math.pow(1 + taxaJurosMensal, 1.0 / 12) - 1;
            int prazo = Integer.parseInt(prazoField.getText());
            TipoImovel tipoImovel = TipoImovel.valueOf(tipoImovelBox.getValue().toUpperCase());
            TipoAmortizacao tipoAmortizacao = TipoAmortizacao.valueOf(tipoFinanciamentoBox.getValue().toUpperCase());

            // Validar entrada
            if (valorEntrada >= valorImovel) {
                mostrarAlerta("Erro", "O valor de entrada não pode ser maior ou igual ao valor do imóvel");
                return;
            }

            // Calcular valor financiado
            double valorFinanciado = valorImovel - valorEntrada;
            System.out.println("Valor Imóvel: " + valorImovel);
            System.out.println("Valor Entrada: " + valorEntrada);
            System.out.println("Valor Financiado: " + valorFinanciado);
            System.out.println("Taxa de Juros: " + taxaJuros);
            System.out.println("Prazo: " + prazo);

            // Criar objetos de domínio
            cliente = new Cliente(nome, cpf, renda);
            imovel = new Imovel(tipoImovel, valorImovel);

            // Calcular parcelas
            parcelas = tipoAmortizacao.calcularParcela(valorFinanciado, taxaJuros, prazo);

            // Verificar valores das parcelas
            double totalPagar = 0.0;
            for (Parcela p : parcelas) {
                System.out.println("Parcela: " + p.getValorParcela());
                totalPagar += p.getValorParcela();
            }

            // Criar financiamento
            financiamento = new Financiamento(prazo, taxaJuros, tipoAmortizacao, valorEntrada, valorFinanciado, totalPagar);

            // Mostrar resultado
            resultadoLabel.setText(String.format("Financiamento calculado! Total a pagar: R$ %.2f", totalPagar));

            // Salvar no banco e carregar tela de resultados
            salvarSimulacaoNoBanco();
            carregarTelaResultados();

        } catch (NumberFormatException e) {
            mostrarAlerta("Erro de Formato", "Por favor, insira valores numéricos válidos em todos os campos.");
        } catch (IllegalArgumentException e) {
            mostrarAlerta("Erro", "Selecione um tipo de imóvel e financiamento válidos");
        } catch (Exception e) {
            mostrarAlerta("Erro", "Ocorreu um erro ao calcular o financiamento: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void verificarTabelas(Connection conexao) throws SQLException {
        DatabaseSetupDAO dbSetup = new DatabaseSetupDAO();
        dbSetup.criarTabelas(conexao);
    }

    private void salvarSimulacaoNoBanco() {
        Connection conexao = null;
        try {
            conexao = Conexao.conectar();
            conexao.setAutoCommit(false); // Inicia transação

            // 1. Verificar/Criar tabelas
            DatabaseSetupDAO dbSetup = new DatabaseSetupDAO();
            dbSetup.criarTabelas(conexao);

            // 2. Salvar Cliente (com validação adicional)
            if (cliente.getCpf() == null || cliente.getCpf().trim().isEmpty()) {
                throw new IllegalArgumentException("CPF do cliente é obrigatório");
            }

            ClienteDAO clienteDAO = new ClienteDAO();
            clienteDAO.adicionarCliente(cliente, conexao);

            // Verifica se o ID foi atribuído
           // if (cliente.getId() <= 0) {
              //  throw new SQLException("ID do cliente não foi gerado corretamente");
         //   }

            // Restante do código permanece igual...
            // [Continua com a inserção do imóvel, financiamento e parcelas]

          //  conexao.commit(); // Confirma transação somente se tudo der certo
//System.out.println("Simulação salva com sucesso! ID Cliente: " + cliente.getId());

        } catch (SQLException e) {
            if (conexao != null) {
                try {
                    conexao.rollback(); // Reverte em caso de erro
                    System.err.println("Transação revertida: " + e.getMessage());
                } catch (SQLException ex) {
                    System.err.println("Erro ao fazer rollback: " + ex.getMessage());
                }
            }
            mostrarAlerta("Erro no Banco de Dados", "Detalhes: " + e.getMessage());
        } finally {
            if (conexao != null) {
                try {
                    conexao.setAutoCommit(true); // Restaura modo padrão
                    conexao.close();
                } catch (SQLException e) {
                    System.err.println("Erro ao fechar conexão: " + e.getMessage());
                }
            }
        }
    }


    private void carregarTelaResultados() throws IOException {
        String path = "view/simulacaoView.fxml";
        URL url = getClass().getResource("/" + path);

        if (url == null) {
            File file = new File("src/main/resources/" + path);
            if (file.exists()) {
                url = file.toURI().toURL();
            } else {
                throw new IOException("Arquivo FXML não encontrado: " + path);
            }
        }

        FXMLLoader loader = new FXMLLoader(url);
        Stage resultStage = new Stage();
        resultStage.setScene(new Scene(loader.load()));

        ResultPageController controller = loader.getController();
        controller.setDadosFinanciamento(financiamento, cliente, imovel, parcelas);

        resultStage.setTitle("Resultado da Simulação");
        resultStage.show();

        if (primaryStage != null) {
            primaryStage.close();
        }
    }

    @FXML
    private void limparCampos() {
        nomeClienteField.clear();
        cpfClienteField.clear();
        rendaClienteField.clear();
        valorImovelField.clear();
        valorEntradaField.clear();
        taxaJurosField.clear();
        prazoField.clear();
        tipoImovelBox.setValue(null);
        tipoFinanciamentoBox.setValue(null);
        resultadoLabel.setText("");
    }

    private void mostrarAlerta(String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }
}