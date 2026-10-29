package ma.rh.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public final class Models {
 private Models(){}
 public record Site(UUID id,String code,String name,String type){}
 public record Product(UUID id,String name,String brand,String category,boolean active,long version){}
 public record ProductInput(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=80) String brand,@NotBlank @Pattern(regexp="PHONE|ACCESSORY") String category){}
 public record ProductUpdate(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=80) String brand,boolean active,@Min(0) long version){}
 public record Variant(UUID id,UUID productId,String productName,String brand,String category,String sku,String color,Integer ramGb,Integer storageGb,BigDecimal wholesalePrice,BigDecimal retailPrice,int minimumStock,boolean active,long version){}
 public record VariantInput(@NotNull UUID productId,@NotBlank @Size(max=64) String sku,@NotBlank @Size(max=80) String color,@Min(1) @Max(1024) Integer ramGb,@Min(1) @Max(65536) Integer storageGb,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal wholesalePrice,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal retailPrice,@Min(0) @Max(1000000) int minimumStock){}
 public record VariantUpdate(@NotBlank @Size(max=64) String sku,@NotBlank @Size(max=80) String color,@Min(1) @Max(1024) Integer ramGb,@Min(1) @Max(65536) Integer storageGb,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal wholesalePrice,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal retailPrice,@Min(0) @Max(1000000) int minimumStock,boolean active,@Min(0) long version){}
 public record Opening(@NotNull UUID branchId,@NotNull UUID variantId,@NotNull @Min(0) @Max(1000000) Long quantity,@DecimalMin("0") @Digits(integer=10,fraction=4) BigDecimal unitCost,@NotBlank @Size(max=500) String reason){}
 public record Adjustment(@NotNull UUID branchId,@NotNull UUID variantId,@NotNull @Min(-1000000) @Max(1000000) Long delta,@NotBlank @Size(max=500) String reason){}
 public record TransferInput(@NotNull UUID sourceId,@NotNull UUID destinationId,@NotBlank @Size(max=500) String reason,@NotEmpty @Size(max=100) List<@NotNull @Valid ReceiptItem> items){}
 public record ReceiptItem(@NotNull UUID variantId,@NotNull @Min(1) @Max(1000000) Long quantity){}
 public record Receipt(@NotBlank @Size(max=500) String reason,@NotEmpty @Size(max=100) List<@NotNull @Valid ReceiptItem> items){}
 public record Operation(UUID id,String kind){}
 public record Page<T>(List<T> items,int page,int size,long total){}
 public record StockRow(UUID branchId,Variant variant,long quantity,long inTransit,long incoming,boolean initialized,boolean lowStock,long quarantine,long rejected){}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record MovementRow(UUID id,UUID entryId,String kind,String reason,String actor,String createdAt,UUID branchId,UUID variantId,String sku,String label,String bucket,long delta,long quantityAfter,BigDecimal unitCost,UUID transferId){}
 public record TransferLine(UUID variantId,String sku,String label,long quantity,long received,long lost,long remaining){}
 public record Transfer(UUID id,UUID sourceId,UUID destinationId,String sourceName,String destinationName,String status,String createdAt,List<TransferLine> items){}
 record TransferHeader(UUID id,UUID sourceId,UUID destinationId,String sourceName,String destinationName,String createdAt){}
}
