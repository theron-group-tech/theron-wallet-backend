package com.theron.wallet.enums;

public enum NotificationType {
    PIX_RECEIVED("PIX recebido", "Um PIX foi creditado na conta."),
    PIX_SENT("PIX enviado", "Um PIX foi enviado com sucesso."),
    TRANSFER_RECEIVED("Transferência recebida", "Uma transferência interna foi creditada."),
    TRANSFER_SENT("Transferência enviada", "Uma transferência interna foi enviada."),
    TRANSFER_APPROVAL_REQUIRED("Aprovação necessária", "Uma transferência PIX aguarda a sua aprovação."),
    TRANSFER_APPROVED("Transferência aprovada", "A sua transferência PIX foi aprovada."),
    TRANSFER_REJECTED("Transferência rejeitada", "A sua transferência PIX foi rejeitada."),
    LOGIN_NEW_DEVICE("Novo dispositivo", "Um novo dispositivo acessou a sua conta."),
    PASSWORD_CHANGED("Senha alterada", "A senha da sua conta foi alterada.");

    private final String title;
    private final String message;

    NotificationType(String title, String message) {
        this.title = title;
        this.message = message;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }
}
