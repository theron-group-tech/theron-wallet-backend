// src/main/java/com/theron/wallet/dto/request/CreateSubaccountRequest.java
package com.theron.wallet.dto.request;

import com.theron.wallet.enums.CompanyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body to create an Asaas subaccount (POST /v3/accounts). CNPJ only (14 digits).")
public class CreateSubaccountRequest {

    // ── Identity ────────────────────────────────────────────────────────────

    @NotBlank
    @Schema(description = "Full name of the subaccount owner", example = "Alexsandro Costa Nunes")
    private String name;

    @NotBlank
    @Email
    @Schema(description = "Email address — used as login email if loginEmail is not provided",
            example = "alexsandro@empresa.com.br")
    private String email;

    @Schema(description = "Alternative login email. If omitted, email is used.", example = "alexsandro.login@empresa.com.br")
    private String loginEmail;

    @NotBlank
    @Pattern(regexp = "\\d{14}", message = "cpfCnpj must be CNPJ with exactly 14 digits")
    @Schema(description = "CNPJ (14 digits, numbers only)", example = "66625514000140")
    private String cpfCnpj;

    @NotBlank
    @Schema(description = "Brazilian mobile phone — 11 digits: area code + 9 + 8 digits (e.g. 11968604680)",
            example = "11968604680")
    private String mobilePhone;

    @Schema(description = "Landline phone (optional)", example = "1162862055")
    private String phone;

    @Schema(description = "Website URL (optional)", example = "https://empresa.com.br")
    private String site;

    // ── PF / PJ ─────────────────────────────────────────────────────────────

    @NotNull
    @Schema(description = "Company type — required (MEI, LIMITED, INDIVIDUAL, ASSOCIATION)",
            example = "LIMITED")
    private CompanyType companyType;

    // ── Financials ───────────────────────────────────────────────────────────

    @NotNull
    @DecimalMin("0.01")
    @Schema(description = "Monthly income (PF) or monthly revenue (PJ) in BRL",
            example = "10000.00")
    private BigDecimal incomeValue;

    // ── Address ──────────────────────────────────────────────────────────────

    @NotBlank
    @Schema(example = "Rua da Baracela")
    private String address;

    @NotBlank
    @Schema(example = "461")
    private String addressNumber;

    @Schema(example = "Condominio Bloco A")
    private String complement;

    @NotBlank
    @Schema(example = "Sao Paulo")
    private String province;

    @NotBlank
    @Size(min = 8, max = 8)
    @Schema(description = "Brazilian postal code (CEP) — 8 digits, no hyphen", example = "02190120")
    private String postalCode;
}
