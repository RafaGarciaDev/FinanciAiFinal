package financiai.financiai.controller;

import financiai.financiai.model.*;
import financiai.financiai.services.*;
import financiai.financiai.dao.*;
import financiai.financiai.dao.ParcelasDAO;
import financiai.financiai.util.Conexao;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.sql.*;
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
    @FXML private Label statusBancoLabel;

    private Stage primaryStage;
    private boolean bancoPronto = false;
    private Cliente cliente;
    private Imovel imovel;
    private Financiamento financiamento;
    private List<Parcela> parcelas;

    @FXML
    public void initialize() {
        tipoImovelBox.getItems().addAll("Casa", "Apartamento");
        tipoFinanciamentoBox.getItems().addAll("SAC", "PRICE");

        // Verificar estrutura do banco ao iniciar
        verificarEstruturaBanco();
    }

    private void verificarEstruturaBanco() {
        statusBancoLabel.setText("Verificando banco de dados...");
        try (Connection conexao = Conexao.conectar()) {
            // Verificar e criar tabelas se necessário
            ClienteDAO clienteDAO = new ClienteDAO();
            clienteDAO.verificarTabela(conexao);

            ImovelDAO imovelDAO = new ImovelDAO();
            imovelDAO.verificarTabela(conexao);

            FinanciamentoDAO financiamentoDAO = new FinanciamentoDAO();
            financiamentoDAO.verificarTabela(conexao);

            ParcelasDAO parcelasDAO = new ParcelasDAO();
            parcelasDAO.verificarTabela(conexao);

            bancoPronto = true;
            statusBancoLabel.setText("");
        } catch (SQLException e) {
            bancoPronto = false;
            statusBancoLabel.setText("Erro no banco de dados!");
            mostrarAlerta("Erro no Banco", "Não foi possível preparar o banco de dados: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void calcularFinanciamento() {
        if (!bancoPronto) {
            mostrarAlerta("Banco não pronto", "Aguarde a inicialização do banco de dados");
            return;
        }

        try {
            // Validação dos campos
            if (!validarCampos()) return;

            // Coletar dados
            DadosFinanciamento dados = coletarDadosFormulario();

            // Validação de negócio
            if (dados.valorEntrada >= dados.valorImovel) {
                mostrarAlerta("Erro", "Valor de entrada não pode ser maior ou igual ao valor do imóvel");
                return;
            }

            // Criar entidades
            cliente = new Cliente(dados.nome, dados.cpf, dados.renda);
            imovel = new Imovel(dados.tipoImovel, dados.valorImovel);

            // Calcular financiamento
            financiamento = calcularFinanciamento(dados, cliente, imovel);

            // Salvar tudo
            salvarFinanciamentoCompleto(cliente, imovel, financiamento);

            // Mostrar resultado
            resultadoLabel.setText(String.format("Financiamento calculado! Total: R$ %.2f",
                    financiamento.getTotalPagar()));

            mostrarTelaResultados();

        } catch (Exception e) {
            mostrarAlerta("Erro", "Falha no cálculo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private DadosFinanciamento coletarDadosFormulario() throws IllegalArgumentException {
        DadosFinanciamento dados = new DadosFinanciamento();
        try {
            dados.nome = nomeClienteField.getText();
            dados.cpf = cpfClienteField.getText();
            dados.renda = Double.parseDouble(rendaClienteField.getText().replace(",", "."));
            dados.valorImovel = Double.parseDouble(valorImovelField.getText().replace(",", "."));
            dados.valorEntrada = Double.parseDouble(valorEntradaField.getText().replace(",", "."));
            dados.taxaJuros = Double.parseDouble(taxaJurosField.getText().replace(",", ".")) / 100;
            dados.prazo = Integer.parseInt(prazoField.getText());
            dados.tipoImovel = TipoImovel.valueOf(tipoImovelBox.getValue().toUpperCase());
            dados.tipoAmortizacao = TipoAmortizacao.valueOf(tipoFinanciamentoBox.getValue().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Dados inválidos no formulário");
        }
        return dados;
    }

    private boolean validarCampos() {
        // Validação de campos vazios
        if (nomeClienteField.getText().isEmpty() ||
                cpfClienteField.getText().isEmpty() ||
                rendaClienteField.getText().isEmpty() ||
                valorImovelField.getText().isEmpty() ||
                valorEntradaField.getText().isEmpty() ||
                taxaJurosField.getText().isEmpty() ||
                prazoField.getText().isEmpty() ||
                tipoImovelBox.getValue() == null ||
                tipoFinanciamentoBox.getValue() == null) {

            mostrarAlerta("Erro", "Preencha todos os campos obrigatórios");
            return false;
        }

        // Validação de CPF
        try {
            new Cliente("Teste", cpfClienteField.getText(), 1000); // Usa a validação da classe Cliente
        } catch (IllegalArgumentException e) {
            mostrarAlerta("Erro", "CPF inválido: " + e.getMessage());
            return false;
        }

        // Validação de valores numéricos
        try {
            double renda = Double.parseDouble(rendaClienteField.getText().replace(",", "."));
            double valorImovel = Double.parseDouble(valorImovelField.getText().replace(",", "."));
            double valorEntrada = Double.parseDouble(valorEntradaField.getText().replace(",", "."));
            double taxaJuros = Double.parseDouble(taxaJurosField.getText().replace(",", "."));
            int prazo = Integer.parseInt(prazoField.getText());

            if (renda <= 0 || valorImovel <= 0 || valorEntrada < 0 || taxaJuros <= 0 || prazo <= 0) {
                mostrarAlerta("Erro", "Valores devem ser maiores que zero");
                return false;
            }
        } catch (NumberFormatException e) {
            mostrarAlerta("Erro", "Valores numéricos inválidos");
            return false;
        }

        return true;
    }

    private Financiamento calcularFinanciamento(DadosFinanciamento dados, Cliente cliente, Imovel imovel) {
        double valorFinanciado = dados.valorImovel - dados.valorEntrada;
        parcelas = dados.tipoAmortizacao.calcularParcela(valorFinanciado, dados.taxaJuros, dados.prazo, dados.cpf);

        double totalPagar = parcelas.stream().mapToDouble(Parcela::getValorParcela).sum();

        Financiamento financiamento = new Financiamento(
                dados.prazo, dados.taxaJuros, dados.tipoAmortizacao,
                dados.valorEntrada, valorFinanciado, totalPagar
        );

        financiamento.setParcelas(parcelas);
        return financiamento;
    }

    private void salvarFinanciamentoCompleto(Cliente cliente, Imovel imovel, Financiamento financiamento) {
        Connection conexao = null;
        try {
            conexao = Conexao.conectar();
            conexao.setAutoCommit(false);

            // 1. Cliente
            ClienteDAO clienteDAO = new ClienteDAO();
            Cliente clienteExistente = clienteDAO.buscarClientePorCpf(cliente.getCpf(), conexao);
            if (clienteExistente != null) {
                cliente = clienteExistente;
            } else {
                clienteDAO.adicionarCliente(cliente, conexao);
            }

            // 2. Imóvel
            ImovelDAO imovelDAO = new ImovelDAO();
            imovelDAO.adicionarImovel(imovel, conexao);

            // 3. Financiamento
            financiamento.setClienteId(cliente.getId());
            financiamento.setImovelId(imovel.getId());
            financiamento.setDataSimulacao(LocalDate.now());

            FinanciamentoDAO financiamentoDAO = new FinanciamentoDAO();
            financiamentoDAO.adicionarFinanciamento(financiamento, conexao);

            // 4. Parcelas
            ParcelasDAO parcelasDAO = new ParcelasDAO();

            // Atualiza financiamento_id em todas as parcelas
            for (Parcela parcela : financiamento.getParcelas()) {
                parcela.setFinanciamentoId(financiamento.getId());
            }

            // Tenta usar inserção em lote via reflection
            try {
                Method metodoLote = ParcelasDAO.class.getDeclaredMethod("inserirLoteParcelas",
                        List.class, Connection.class);
                metodoLote.setAccessible(true);
                metodoLote.invoke(parcelasDAO, financiamento.getParcelas(), conexao);
            } catch (Exception e) {
                // Fallback para inserção individual
                for (Parcela parcela : financiamento.getParcelas()) {
                    parcelasDAO.adicionarParcela(parcela, conexao);
                }
            }

            conexao.commit();
        } catch (SQLException e) {
            if (conexao != null) {
                try {
                    conexao.rollback();
                } catch (SQLException ex) {
                    mostrarAlerta("Erro no Rollback", "Falha ao reverter transação: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            mostrarAlerta("Erro no Banco", "Falha ao salvar: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conexao != null) {
                try {
                    conexao.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private static class DadosFinanciamento {
        String nome;
        String cpf;
        double renda;
        double valorImovel;
        double valorEntrada;
        double taxaJuros;
        int prazo;
        TipoImovel tipoImovel;
        TipoAmortizacao tipoAmortizacao;
    }

    private void mostrarAlerta(String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    @FXML
    private void mostrarTelaResultados() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/simulacaoView.fxml"));
            Parent root = loader.load();
            // Para carregar a view de parcelas
            //FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/parcelaView.fxml"));

            ResultPageController controller = loader.getController();
            controller.setDadosFinanciamento(financiamento, cliente, imovel, parcelas);

            Stage stage = (Stage) nomeClienteField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            mostrarAlerta("Erro", "Não foi possível carregar a tela de resultados: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void buscarParcelasPorCPF() {
        try {
            String cpf = cpfClienteField.getText().replaceAll("[^0-9]", "");

            if (cpf.isEmpty()) {
                mostrarAlerta("Erro", "Informe um CPF para buscar");
                return;
            }

            try (Connection conexao = Conexao.conectar()) {
                ParcelasDAO parcelasDAO = new ParcelasDAO();
                List<Parcela> parcelas = parcelasDAO.buscarParcelasPorCpfCliente(cpf, conexao);

                if (parcelas.isEmpty()) {
                    mostrarAlerta("Informação", "Nenhuma parcela encontrada para este CPF");
                    return;
                }

                // Carrega a tela de visualização de parcelas
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/parcelaView.fxml"));
                URL url = getClass().getResource("/view/parcelaView.fxml");

                if (url == null) {
                    throw new RuntimeException("Arquivo FXML não encontrado. Verifique se o arquivo parcelaView.fxml está em: " +
                            "src/main/resources/view/parcelaView.fxml");

                }

                Parent root = loader.load();

                ParcelaViewController controller = loader.getController();
                controller.setParcelas(parcelas);

                Stage stage = (Stage) cpfClienteField.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.show();
            }
        } catch (SQLException | IOException e) {
            mostrarAlerta("Erro", "Falha ao buscar parcelas: " + e.getMessage());
            e.printStackTrace();
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

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
}







































































