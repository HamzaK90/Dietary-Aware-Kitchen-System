package com.cookmgmt.repository;

import com.cookmgmt.domain.Customer;

/** Stores customers, keyed by identity and unique by email. */
public class CustomerRepository extends PersonRepository<Customer> {
}
