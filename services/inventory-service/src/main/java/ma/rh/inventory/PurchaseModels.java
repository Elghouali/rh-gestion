package ma.rh.inventory;
import com.fasterxml.jackson.annotation.JsonInclude;
import ma.rh.inventory.Models.ReceiptItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public final class PurchaseModels {
 private PurchaseModels(){}
 public record OrderInput(@NotNull UUID supplierId,@NotNull UUID branchId,@NotBlank @Size(max=100) String reference,@NotBlank @Size(max=500) String note,@NotEmpty @Size(max=100) List<@NotNull @Valid OrderItem> items){}
 public record OrderItem(@NotNull UUID variantId,@NotNull @Min(1) @Max(1000000) Long quantity,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=4) BigDecimal unitCost){}
 public record ReceiptInput(@NotBlank @Size(max=100) String reference,@NotBlank @Size(max=500) String reason,@NotEmpty @Size(max=100) List<@NotNull @Valid ReceiptItem> items){}
 public record InspectionInput(@NotBlank @Size(max=500) String reason,@NotEmpty @Size(max=100) List<@NotNull @Valid InspectionItem> items){}
 public record InspectionItem(@NotNull UUID variantId,@NotNull @Min(0) @Max(1000000) Long accepted,@NotNull @Min(0) @Max(1000000) Long rejected){}
 public record CloseInput(@NotBlank @Size(max=500) String reason){}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record OrderLine(UUID variantId,String sku,String label,long quantity,long received,long remaining,BigDecimal unitCost){}
 public record Order(UUID id,UUID supplierId,String supplierName,UUID branchId,String branchName,String reference,String note,String status,long version,String createdAt,List<OrderLine> items,List<Delivery> receipts){}
 public record DeliveryLine(UUID variantId,String sku,String label,long quantity,long accepted,long rejected,long pending){}
 public record Delivery(UUID id,String reference,String reason,String actor,String createdAt,List<DeliveryLine> items){}
 record Header(UUID id,UUID supplierId,String supplierName,UUID branchId,String branchName,String reference,String note,boolean closed,long version,String createdAt){}
 record RawLine(UUID variantId,String sku,String label,long quantity,long received,BigDecimal unitCost){}
}
