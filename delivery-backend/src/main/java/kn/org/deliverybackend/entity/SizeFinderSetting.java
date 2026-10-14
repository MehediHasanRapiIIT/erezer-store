package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The size finder's rules for one fit, for products with no fit (_GENERAL), or the shop-wide switch (_SHOP). */
@Entity
@Table(name = "size_finder")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SizeFinderSetting {

    @Id
    @Column(length = 40)
    private String scope;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "rules_json", columnDefinition = "text")
    private String rulesJson;
}
