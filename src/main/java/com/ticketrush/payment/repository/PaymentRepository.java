package com.ticketrush.payment.repository;

import com.ticketrush.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderRef(String providerRef);

    Optional<Payment> findByBookingId(Long bookingId);
}
