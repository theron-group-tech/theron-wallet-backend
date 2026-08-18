package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.SubaccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subaccounts")
@RequiredArgsConstructor
@Tag(name = "Subaccounts", description = "Gerenciamento de contas-filha (subcontas) no Asaas — backoffice only")
public class SubaccountController {

    private final SubaccountService subaccountService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @PostMapping
    @Operation(
            summary = "Criar subconta no Asaas",
            description = """
                    Cria uma conta-filha no Asaas via POST /v3/accounts usando a chave raiz do Theron.
                    
                    - Para Pessoa Física (CPF): `birthDate` é obrigatório. `companyType` é ignorado.
                    - Para Pessoa Jurídica (CNPJ): `companyType` é obrigatório (MEI, LIMITED, INDIVIDUAL, ASSOCIATION). `birthDate` é ignorado.
                    - O webhook é registrado **inline** na mesma chamada de criação (atômico).
                    - A API key retornada pelo Asaas é criptografada e armazenada — **nunca** retornada nas respostas.
                    - O status inicial é `PROVISIONING`. Em caso de sucesso vira `PENDING_EVALUATION`; em falha, `FAILED`.
                    - Se uma tentativa anterior ficou em `FAILED`, uma nova requisição com o mesmo CPF/CNPJ faz **retry** automático.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subconta criada e em avaliação regulatória (PENDING_EVALUATION)"),
            @ApiResponse(responseCode = "400", description = "Erro de validação nos campos"),
            @ApiResponse(responseCode = "409", description = "Já existe uma subconta ativa para este CPF/CNPJ"),
            @ApiResponse(responseCode = "422", description = "Erro de validação retornado pelo Asaas")
    })
    public ResponseEntity<SubaccountResponse> create(@Valid @RequestBody CreateSubaccountRequest request) {
        actorResolver.requireProductUserId();
        SubaccountResponse response = subaccountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Listar subcontas (paginado)",
            description = "Retorna todas as subcontas com suporte a paginação e filtro opcional por status. "
                    + "Exemplo: `GET /api/v1/subaccounts?status=ACTIVE&page=0&size=20&sort=createdAt,desc`"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    public ResponseEntity<Page<SubaccountResponse>> findAll(
            @RequestParam(required = false) SubaccountStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        actorResolver.requireProductUserId();
        throw new ForbiddenException("Listing all subaccounts is not allowed");
    }

    @GetMapping("/{subaccountId}")
    @Operation(
            summary = "Buscar subconta por ID interno",
            description = "Retorna os detalhes de uma subconta pelo ID interno do Theron. A API key nunca é incluída na resposta."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subconta encontrada"),
            @ApiResponse(responseCode = "404", description = "Subconta não encontrada")
    })
    public ResponseEntity<SubaccountResponse> findById(@PathVariable UUID subaccountId) {
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), subaccountId, PermissionCodes.WALLET_READ);
        return ResponseEntity.ok(subaccountService.findById(subaccountId));
    }

    @GetMapping("/cpf-cnpj/{cpfCnpj}")
    @Operation(
            summary = "Buscar subconta por CPF/CNPJ",
            description = "Retorna a subconta correspondente ao CPF ou CNPJ informado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subconta encontrada"),
            @ApiResponse(responseCode = "404", description = "Subconta não encontrada para este CPF/CNPJ")
    })
    public ResponseEntity<SubaccountResponse> findByCpfCnpj(@PathVariable String cpfCnpj) {
        SubaccountResponse response = subaccountService.findByCpfCnpj(cpfCnpj);
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), response.getId(), PermissionCodes.WALLET_READ);
        return ResponseEntity.ok(response);
    }
}
