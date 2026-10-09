package com.material.controller;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.material.model.Produto;
import com.material.model.Entrada;
import com.material.model.EntradaImpostos;
import com.material.model.EntradaProdutos;
import com.material.model.OrigemSistema;
import com.material.repository.ProdutoRepository;
import com.material.repository.EntradaRepository;
import com.material.repository.OrigemSistemaRepository;
import com.material.repository.CarrinhoImpostosRepository;
import com.material.repository.EntradaImpostosRepository;
import com.material.repository.EntradaProdutosRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.awt.Color;
import java.util.List;
import java.util.Locale;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {
	
	@Autowired
	private CarrinhoImpostosRepository carrinhoImpostosRepository;

	@Autowired
    private com.material.repository.CarrinhoRepository carrinhoRepository;
	
	@Autowired
	private ProdutoRepository produtoRepository;

	@Autowired
	private EntradaRepository entradaRepository;

	@Autowired
	private EntradaImpostosRepository impostosRepository;

	@Autowired
	private EntradaProdutosRepository entradaProdutosRepository;

	@Autowired
	private OrigemSistemaRepository origemSistemaRepository;

	// ==========================================================
	// 🌳 ENDPOINT 1: MAPA DE INVENTÁRIO DE MERCADORIAS (MIM)
	// ==========================================================
	@GetMapping("/mim")
	public ResponseEntity<byte[]> gerarMim() {
		OrigemSistema origem = origemSistemaRepository.findByOrigemSistemaTrueAndUnidadeAtivaTrue()
				.orElseThrow(() -> new RuntimeException("Nenhuma origem ativa configurada."));

		List<Produto> produtos = produtoRepository.findAll();

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Document document = new Document(PageSize.A4, 20, 20, 20, 20);
			PdfWriter.getInstance(document, out);

			document.open();

			Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
			Font fontCorpo = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
	
			adicionarCabecalhoOrigem(document, origem, fontCorpo);
			
			PdfPTable table = new PdfPTable(new float[] { 10f, 40f, 15f, 15f, 20f });
			table.setWidthPercentage(100);

			String[] headers = { "ID", "PRODUTO", "SALDO ESTOQUE", "PREÇO CUSTO", "VALOR TOTAL" };

			for (int i = 0; i < headers.length; i++) {
				PdfPCell cell = new PdfPCell(new Phrase(headers[i], fontHeader));
				cell.setBackgroundColor(new Color(46, 125, 50));
				cell.setPadding(8);
				cell.setBorder(PdfPCell.NO_BORDER);
				if (i >= 2)
					cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
				else
					cell.setHorizontalAlignment(Element.ALIGN_LEFT);
				table.addCell(cell);
			}
			BigDecimal valorPatrimonialTotal = BigDecimal.ZERO;
			Color borderCol = new Color(229, 231, 235);

			for (Produto p : produtos) {
				PdfPCell cId = new PdfPCell(new Phrase(String.valueOf(p.getId()), fontCorpo));
				configurarBordaFina(cId, Element.ALIGN_LEFT, borderCol);
				table.addCell(cId);

				PdfPCell cNome = new PdfPCell(new Phrase(p.getNome(), fontCorpo));
				configurarBordaFina(cNome, Element.ALIGN_LEFT, borderCol);
				table.addCell(cNome);

				BigDecimal qtde = p.getAGranel() ? p.getEstoque() : BigDecimal.valueOf(p.getEstoqueAtual());
				PdfPCell cQtde = new PdfPCell(new Phrase(qtde.toString(), fontCorpo));
				configurarBordaFina(cQtde, Element.ALIGN_RIGHT, borderCol);
				table.addCell(cQtde);

				BigDecimal custo = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
				PdfPCell cCusto = new PdfPCell(new Phrase("R$ " + String.format("%.2f", custo), fontCorpo));
				configurarBordaFina(cCusto, Element.ALIGN_RIGHT, borderCol);
				table.addCell(cCusto);

				BigDecimal totalItem = qtde.multiply(custo);
				valorPatrimonialTotal = valorPatrimonialTotal.add(totalItem);

				PdfPCell cTotal = new PdfPCell(new Phrase("R$ " + String.format("%.2f", totalItem), fontCorpo));
				configurarBordaFina(cTotal, Element.ALIGN_RIGHT, borderCol);
				table.addCell(cTotal);
			}

			document.add(table);

			Font fontTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(198, 40, 40));
			Paragraph totalGeral = new Paragraph(
					"\n💰 VALOR PATRIMONIAL TOTAL EM ESTOQUE: R$ " + String.format("%.2f", valorPatrimonialTotal),
					fontTotal);
			totalGeral.setAlignment(Element.ALIGN_RIGHT);
			document.add(totalGeral);

			document.close();

			HttpHeaders headersHttp = new HttpHeaders();
			headersHttp.setContentType(MediaType.APPLICATION_PDF);
			headersHttp.setContentDispositionFormData("filename", "Mapa_Inventario_Mercadorias.pdf");

			return new ResponseEntity<>(out.toByteArray(), headersHttp, HttpStatus.OK);

		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	// ==========================================================
		// 📈 ENDPOINT 2: MAPA GERENCIAL DE COMPRAS (MGC)
		// ==========================================================
		@GetMapping("/mgc")
		public ResponseEntity<byte[]> gerarMgc() {
			OrigemSistema origem = origemSistemaRepository.findByOrigemSistemaTrueAndUnidadeAtivaTrue()
					.orElseThrow(() -> new RuntimeException("Nenhuma origem ativa configurada."));

			List<Produto> produtos = produtoRepository.findAll();

			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				Document document = new Document(PageSize.A4, 20, 20, 20, 20);
				PdfWriter.getInstance(document, out);

				document.open();

				Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
				Paragraph titulo = new Paragraph("📈 MGC - MAPA GERENCIAL DE COMPRAS (MARGENS DE LUCRO)", fontTitulo);
				titulo.setAlignment(Element.ALIGN_CENTER);
				titulo.setSpacingAfter(20);
				document.add(titulo);

				PdfPTable table = new PdfPTable(new float[] { 25f, 25f, 12f, 13f, 13f, 12f });
				table.setWidthPercentage(100);

				Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
				String[] headers = { "PRODUTO", "FORNECEDOR", "P. CUSTO", "P. VENDA", "LUCRO (R$)", "MARGEM %" };
				
				Font fontCorpo = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
				
				adicionarCabecalhoOrigem(document, origem, fontCorpo);
				
				for (int i = 0; i < headers.length; i++) {
					PdfPCell cell = new PdfPCell(new Phrase(headers[i], fontHeader));
					cell.setBackgroundColor(new Color(2, 136, 209));
					cell.setPadding(8);
					cell.setBorder(PdfPCell.NO_BORDER);

					if (i >= 2 && i <= 4)
						cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
					else if (i == 5)
						cell.setHorizontalAlignment(Element.ALIGN_CENTER);
					else
						cell.setHorizontalAlignment(Element.ALIGN_LEFT);

					table.addCell(cell);
				}

				Color borderCol = new Color(229, 231, 235);
				Color cinzaZebra = new Color(249, 250, 251);
				boolean alternarCor = false;

				for (Produto p : produtos) {
					Color corFundo = alternarCor ? cinzaZebra : Color.WHITE;
					alternarCor = !alternarCor;

					PdfPCell cProd = new PdfPCell(new Phrase(p.getNome(), fontCorpo));
					configurarBordaFinaComFundo(cProd, Element.ALIGN_LEFT, borderCol, corFundo);
					table.addCell(cProd);

					String nomeForn = "Não Informado";
					PdfPCell cForn = new PdfPCell(new Phrase(nomeForn, fontCorpo));
					configurarBordaFinaComFundo(cForn, Element.ALIGN_LEFT, borderCol, corFundo);
					table.addCell(cForn);

					BigDecimal custo = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
					PdfPCell cCusto = new PdfPCell(new Phrase("R$ " + String.format("%.2f", custo), fontCorpo));
					configurarBordaFinaComFundo(cCusto, Element.ALIGN_RIGHT, borderCol, corFundo);
					table.addCell(cCusto);

					BigDecimal venda = p.getPrecoVenda() != null ? p.getPrecoVenda() : BigDecimal.ZERO;
					PdfPCell cVenda = new PdfPCell(new Phrase("R$ " + String.format("%.2f", venda), fontCorpo));
					configurarBordaFinaComFundo(cVenda, Element.ALIGN_RIGHT, borderCol, corFundo);
					table.addCell(cVenda);

					BigDecimal lucro = venda.subtract(custo);
					PdfPCell cLucro = new PdfPCell(new Phrase("R$ " + String.format("%.2f", lucro), fontCorpo));
					configurarBordaFinaComFundo(cLucro, Element.ALIGN_RIGHT, borderCol, corFundo);
					table.addCell(cLucro);

					BigDecimal margem = BigDecimal.ZERO;
					if (custo.compareTo(BigDecimal.ZERO) > 0) {
						margem = lucro.multiply(BigDecimal.valueOf(100)).divide(custo, 2, RoundingMode.HALF_UP);
					}
					PdfPCell cMargem = new PdfPCell(new Phrase(margem.toString() + "%", fontCorpo));
					configurarBordaFinaComFundo(cMargem, Element.ALIGN_CENTER, borderCol, corFundo);
					table.addCell(cMargem);
				}

				document.add(table);
				document.close();

				HttpHeaders headersHttp = new HttpHeaders();
				headersHttp.setContentType(MediaType.APPLICATION_PDF);
				headersHttp.setContentDispositionFormData("filename", "Mapa_Gerencial_Compras.pdf");

				return new ResponseEntity<>(out.toByteArray(), headersHttp, HttpStatus.OK);

			} catch (Exception e) {
				e.printStackTrace();
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
		
		// ==========================================================
		// 🧾 ENDPOINT 3: ESCRITURAÇÃO FISCAL CONSOLIDADA (APURAÇÃO)
		// ==========================================================
		@GetMapping("/escrituracao-fiscal")
		public ResponseEntity<byte[]> gerarRelatorioFiscal(
				@RequestParam("dataInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
				@RequestParam("dataFim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
			try {
				LocalDateTime inicio = dataInicio.atStartOfDay();
				LocalDateTime fim = dataFim.atTime(23, 59, 59);

				Object dadosEntradaRaw = impostosRepository.somarImpostosPeriodo(inicio, fim);
				Object dadosSaidaRaw = carrinhoImpostosRepository.somarImpostosPeriodo(inicio, fim);

				BigDecimal totalNotaEntrada = BigDecimal.ZERO;
				BigDecimal totalIcmsEntrada = BigDecimal.ZERO;
				BigDecimal totalIpiEntrada = BigDecimal.ZERO;

				BigDecimal totalNotaSaida = BigDecimal.ZERO;
				BigDecimal totalIcmsSaida = BigDecimal.ZERO;
				BigDecimal totalIpiSaida = BigDecimal.ZERO;

				// --- TRATAMENTO SEGURO E BLINDADO DE ENTRADAS (SQL NATIVO) ---
				// --- EXTRAÇÃO ROBUSTA DE ENTRADAS (SQL NATIVO BLINDADO) ---
				if (dadosEntradaRaw != null) {
					try {
						// Se o driver retornar como array de objetos
						Object[] colunas = (Object[]) dadosEntradaRaw;
						if (colunas != null && colunas.length > 0 && colunas[0] != null) totalNotaEntrada = new BigDecimal(colunas[0].toString());
						if (colunas != null && colunas.length > 1 && colunas[1] != null) totalIcmsEntrada = new BigDecimal(colunas[1].toString());
						if (colunas != null && colunas.length > 2 && colunas[2] != null) totalIpiEntrada = new BigDecimal(colunas[2].toString());
					} catch (ClassCastException e) {
						// CASO DE BASE LIMPA: Se o driver entregar uma lista interna ou objeto plano
						try {
							java.util.List<?> lista = (java.util.List<?>) dadosEntradaRaw;
							if (lista != null && !lista.isEmpty()) {
								Object[] colunas = (Object[]) lista.get(0);
								if (colunas[0] != null) totalNotaEntrada = new BigDecimal(colunas[0].toString());
								if (colunas[1] != null) totalIcmsEntrada = new BigDecimal(colunas[1].toString());
								if (colunas[2] != null) totalIpiEntrada = new BigDecimal(colunas[2].toString());
							}
						} catch (Exception ex) {
							// Se vier como valor único escalar do MySQL
							try {
								totalNotaEntrada = new BigDecimal(dadosEntradaRaw.toString());
							} catch (Exception e3) { System.out.println("ℹ️ Sem dados de entrada registrados."); }
						}
					}
				}

				// --- EXTRAÇÃO ROBUSTA DE SAÍDAS (SQL NATIVO BLINDADO) ---
				if (dadosSaidaRaw != null) {
					try {
						Object[] colunas = (Object[]) dadosSaidaRaw;
						if (colunas != null && colunas.length > 0 && colunas[0] != null) totalNotaSaida = new BigDecimal(colunas[0].toString());
						if (colunas != null && colunas.length > 1 && colunas[1] != null) totalIcmsSaida = new BigDecimal(colunas[1].toString());
						if (colunas != null && colunas.length > 2 && colunas[2] != null) totalIpiSaida = new BigDecimal(colunas[2].toString());
					} catch (ClassCastException e) {
						// CASO DE BASE LIMPA: Se o driver entregar uma lista interna ou objeto plano
						try {
							java.util.List<?> lista = (java.util.List<?>) dadosSaidaRaw;
							if (lista != null && !lista.isEmpty()) {
								Object[] colunas = (Object[]) lista.get(0);
								if (colunas[0] != null) totalNotaSaida = new BigDecimal(colunas[0].toString());
								if (colunas[1] != null) totalIcmsSaida = new BigDecimal(colunas[1].toString());
								if (colunas[2] != null) totalIpiSaida = new BigDecimal(colunas[2].toString());
							}
						} catch (Exception ex) {
							try {
								totalNotaSaida = new BigDecimal(dadosSaidaRaw.toString());
							} catch (Exception e3) { System.out.println("ℹ️ Sem dados de saída registrados."); }
						}
					}
				}
				BigDecimal saldoIcms = totalIcmsSaida.subtract(totalIcmsEntrada);
				BigDecimal saldoIpi = totalIpiSaida.subtract(totalIpiEntrada);

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				Document document = new Document(PageSize.A4, 20, 20, 20, 20);
				PdfWriter.getInstance(document, out);
				document.open();

				Font fonteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Color.DARK_GRAY);
				Font fonteSubtitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.GRAY);
				Font fonteNormal = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
				Font fontTabelaHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

				NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

				Paragraph title = new Paragraph("SISTEMA MAGIA - MATERIAL DE CONSTRUÇÃO", fonteTitulo);
				title.setAlignment(Element.ALIGN_CENTER);
				document.add(title);

				Paragraph subtitle = new Paragraph("LIVRO GERENCIAL DE ESCRITURAÇÃO E APURAÇÃO FISCAL\nPeríodo: " + dataInicio + " até " + dataFim + "\n\n", fonteSubtitulo);
				subtitle.setAlignment(Element.ALIGN_CENTER);
				document.add(subtitle);

				PdfPTable tabela = new PdfPTable(4);
				tabela.setWidthPercentage(100);

				String[] headers = {"Operação / Lote", "Total do Movimento", "Total ICMS", "Total IPI"};
				for (String h : headers) {
					PdfPCell cell = new PdfPCell(new Paragraph(h, fontTabelaHeader));
					cell.setBackgroundColor(new Color(230, 126, 34));
					cell.setPadding(6);
					cell.setHorizontalAlignment(Element.ALIGN_CENTER);
					tabela.addCell(cell);
				}

				tabela.addCell(new Paragraph("ENTRADAS (Crédito fiscal)", fonteNormal));
				tabela.addCell(new Paragraph(nf.format(totalNotaEntrada), fonteNormal));
				tabela.addCell(new Paragraph(nf.format(totalIcmsEntrada), fonteNormal));
				tabela.addCell(new Paragraph(nf.format(totalIpiEntrada), fonteNormal));

				tabela.addCell(new Paragraph("SAÍDAS (Débito fiscal)", fonteNormal));
				tabela.addCell(new Paragraph(nf.format(totalNotaSaida), fonteNormal));
				tabela.addCell(new Paragraph(nf.format(totalIcmsSaida), fonteNormal));
				tabela.addCell(new Paragraph(nf.format(totalIpiSaida), fonteNormal));

				document.add(tabela);

				document.add(new Paragraph("\n----------------------------------------------------------------------------------"));
				document.add(new Paragraph("RESUMO CONSOLIDADO DO SALDO DO PERÍODO\n\n", fonteSubtitulo));

				if (saldoIcms.compareTo(BigDecimal.ZERO) >= 0) {
					document.add(new Paragraph("=> ICMS TOTAL A RECOLHER: " + nf.format(saldoIcms), fonteSubtitulo));
				} else {
					document.add(new Paragraph("=> SALDO CREDOR DE ICMS (Acumulado): " + nf.format(saldoIcms.abs()), fonteNormal));
				}

				if (saldoIpi.compareTo(BigDecimal.ZERO) >= 0) {
					document.add(new Paragraph("=> IPI TOTAL A RECOLHER: " + nf.format(saldoIpi), fonteSubtitulo));
				} else {
					document.add(new Paragraph("=> SALDO CREDOR DE IPI (Acumulado): " + nf.format(saldoIpi.abs()), fonteNormal));
				}

				document.close();

				HttpHeaders headersHttp = new HttpHeaders();
				headersHttp.setContentType(MediaType.APPLICATION_PDF);
				headersHttp.setContentDispositionFormData("filename", "Escrituracao_Fiscal.pdf");

				return new ResponseEntity<>(out.toByteArray(), headersHttp, HttpStatus.OK);

			} catch (Exception e) {
				e.printStackTrace();
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		// ==========================================================
		// MÉTODOS AUXILIARES DE ESTILIZAÇÃO E CABEÇALHO
		// ==========================================================
		// ==========================================================
		// MÉTODOS AUXILIARES DE ESTILIZAÇÃO E CABEÇALHO
		// ==========================================================
		private void adicionarCabecalhoOrigem(Document document, OrigemSistema origem, Font fontCorpo) throws DocumentException {
			// 🎯 CORRIGIDO: Trocado getRazaoSocial() pelo getter real da sua Model (.getNome())
			Paragraph pOrigem = new Paragraph(origem.getNome() + " | CNPJ: " + origem.getCnpj() + "\n\n", fontCorpo);
			pOrigem.setAlignment(Element.ALIGN_LEFT);
			document.add(pOrigem);
		}
		private void configurarBordaFina(PdfPCell cell, int alinhamento, Color corBorda) {
			cell.setBorderColor(corBorda);
			cell.setBorderWidth(0.5f);
			cell.setPadding(6);
			cell.setHorizontalAlignment(alinhamento);
		}

		private void configurarBordaFinaComFundo(PdfPCell cell, int alinhamento, Color corBorda, Color corFundo) {
			configurarBordaFina(cell, alinhamento, corBorda);
			cell.setBackgroundColor(corFundo);
		}
	}
		
		
		
	
	
	
	
	
	
	
	
	
	