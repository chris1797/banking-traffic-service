package com.banking.core.domain

import jakarta.persistence.*

@Entity
@Table(name = "account")
open class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    var id: Long? = null
) : BaseEntity()