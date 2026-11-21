package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.enumeration.CustomOrderStatus;
import kn.org.deliverybackend.entity.Users;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Everything kept about one customer, across tables, for deleting their account
 * (AccountDeletionServiceImpl). Orders are kept for the shop's records with the
 * person taken out; what only served the customer is deleted.
 */
public interface CustomerDataRepository extends Repository<Users, UUID> {

    /** Their orders that are not finished yet (the courier still needs the address). */
    @Query("select o.orderNumber from Order o where o.clientId = :id and upper(o.orderStatus) not in :finished")
    List<String> openOrderNumbers(@Param("id") UUID id, @Param("finished") Collection<String> finished);

    /** Their custom design requests that are still being quoted or made. */
    @Query("select c.reference from CustomOrder c where c.userId = :id and c.status not in :finished")
    List<String> openCustomOrders(@Param("id") UUID id, @Param("finished") Collection<CustomOrderStatus> finished);

    @Modifying
    @Query("update Order o set o.customerName = :name, o.customerEmail = null, o.customerPhone = null, "
            + "o.deliveryAddress = :address, o.latitude = null, o.longitude = null, o.pushToken = null "
            + "where o.clientId = :id")
    int anonymizeOrders(@Param("id") UUID id, @Param("name") String name, @Param("address") String address);

    /** Custom orders need some value in each contact field; these stand in for the removed ones. */
    @Modifying
    @Query("update CustomOrder c set c.firstName = 'Deleted', c.lastName = 'customer', c.phone = '-', "
            + "c.email = :email, c.shippingAddress = :address, c.apartment = null, c.city = '-', c.zipCode = null "
            + "where c.userId = :id")
    int anonymizeCustomOrders(@Param("id") UUID id, @Param("email") String email, @Param("address") String address);

    @Modifying
    @Query("update ReturnRequest r set r.customerEmail = null where r.userId = :id")
    int anonymizeReturns(@Param("id") UUID id);

    @Modifying
    @Query("delete from Addresses a where a.consumerId = :id")
    int deleteAddresses(@Param("id") UUID id);

    @Modifying
    @Query("delete from Cart c where c.userId = :id")
    int deleteCart(@Param("id") UUID id);

    @Modifying
    @Query("delete from CustomDesignDraft d where d.userId = :id")
    int deleteDesignDrafts(@Param("id") UUID id);

    @Modifying
    @Query("delete from Notifications n where n.userId = :id")
    int deleteNotifications(@Param("id") UUID id);

    @Modifying
    @Query("delete from OtpVerification o where o.userId = :id")
    int deleteOneTimeCodes(@Param("id") UUID id);

    @Modifying
    @Query("delete from NewsletterSubscriber n where lower(n.email) = lower(:email)")
    int deleteNewsletterSignup(@Param("email") String email);

    @Modifying
    @Query("delete from PushDevice p where p.userId = :id")
    int deletePushDevices(@Param("id") UUID id);

    @Modifying
    @Query("delete from Users u where u.id = :id")
    int deleteAccount(@Param("id") UUID id);
}
