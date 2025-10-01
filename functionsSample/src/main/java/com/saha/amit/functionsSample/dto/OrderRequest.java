package com.saha.amit.functionsSample.dto;

public class OrderRequest {
    private String orderId;
    private String item;
    private int quantity;

    public OrderRequest() {} // required for JSON deserialization

    public OrderRequest(String orderId, String item, int quantity) {
        this.orderId = orderId;
        this.item = item;
        this.quantity = quantity;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
