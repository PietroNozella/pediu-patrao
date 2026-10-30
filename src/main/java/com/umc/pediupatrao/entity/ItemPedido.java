package com.umc.pediupatrao.entity;

import java.math.BigDecimal;

public class ItemPedido {
    private String produtoId;
    private String nomeProduto;
    private String tipoProduto;
    private int quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;

    public ItemPedido() {
    }

    public ItemPedido(String produtoId, String nomeProduto, int quantidade,
                      BigDecimal precoUnitario, BigDecimal subtotal) {
        this(produtoId, nomeProduto, null, quantidade, precoUnitario, subtotal);
    }

    public ItemPedido(String produtoId, String nomeProduto, String tipoProduto, int quantidade,
                      BigDecimal precoUnitario, BigDecimal subtotal) {
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.tipoProduto = tipoProduto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.subtotal = subtotal;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public void setNomeProduto(String nomeProduto) {
        this.nomeProduto = nomeProduto;
    }

    public String getTipoProduto() {
        return tipoProduto;
    }

    public void setTipoProduto(String tipoProduto) {
        this.tipoProduto = tipoProduto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
