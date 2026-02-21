package com.scanales.eventflow.public_;

import com.scanales.eventflow.economy.EconomyCatalogItem;
import com.scanales.eventflow.economy.EconomyInventoryItem;
import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.economy.EconomyTransaction;
import com.scanales.eventflow.economy.EconomyWallet;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Path("/api/economy")
@Produces(MediaType.APPLICATION_JSON)
public class EconomyApiResource {

  @Inject EconomyService economyService;
  @Inject UsageMetricsService metrics;
  @Inject SecurityIdentity identity;

  @GET
  @Path("/catalog")
  public Response catalog() {
    Optional<String> userId = currentUserId();
    if (userId.isPresent()) {
      List<EconomyService.CatalogOffer> items = economyService.listCatalogForUser(userId.get());
      return Response.ok(Map.of("items", items, "count", items.size(), "personalized", true)).build();
    }
    List<EconomyCatalogItem> items = economyService.listCatalog();
    return Response.ok(Map.of("items", items, "count", items.size(), "personalized", false)).build();
  }

  @GET
  @Path("/wallet")
  @Authenticated
  public Response wallet() {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      EconomyWallet wallet = economyService.getWallet(userId.get());
      return Response.ok(Map.of("wallet", wallet)).build();
    } catch (EconomyService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/inventory")
  @Authenticated
  public Response inventory(
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      int limit = limitParam == null ? 20 : limitParam;
      int offset = offsetParam == null ? 0 : offsetParam;
      List<EconomyInventoryItem> items = economyService.listInventory(userId.get(), limit, offset);
      return Response.ok(new InventoryResponse(limit, offset, items)).build();
    } catch (EconomyService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/transactions")
  @Authenticated
  public Response transactions(
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      int limit = limitParam == null ? 20 : limitParam;
      int offset = offsetParam == null ? 0 : offsetParam;
      EconomyService.TransactionPage page = economyService.listTransactions(userId.get(), limit, offset);
      return Response.ok(
          new TransactionsResponse(
              page.limit(),
              page.offset(),
              page.total(),
              page.partial(),
              page.items()))
          .build();
    } catch (EconomyService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @POST
  @Path("/purchase")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response purchase(PurchaseRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      String itemId = request != null ? request.itemId() : null;
      EconomyService.PurchaseResult result = economyService.purchase(userId.get(), itemId);
      metrics.recordFunnelStep("economy_purchase");
      return Response.ok(Map.of("purchase", result)).build();
    } catch (EconomyService.ValidationException e) {
      return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
    } catch (EconomyService.CapacityException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "economy_capacity_guardrail", "detail", e.getMessage()))
          .build();
    }
  }

  @GET
  @Path("/stats")
  @Authenticated
  public Response stats() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    return Response.ok(Map.of("economy", economyService.metrics())).build();
  }

  private Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal != null && !principal.isBlank()) {
      return Optional.of(principal.toLowerCase(Locale.ROOT));
    }
    String sub = AdminUtils.getClaim(identity, "sub");
    if (sub != null && !sub.isBlank()) {
      return Optional.of(sub);
    }
    return Optional.empty();
  }

  public record PurchaseRequest(String itemId) {
  }

  public record InventoryResponse(int limit, int offset, List<EconomyInventoryItem> items) {
  }

  public record TransactionsResponse(
      int limit,
      int offset,
      long total,
      boolean partial,
      List<EconomyTransaction> items) {
  }
}
