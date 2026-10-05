package com.evops.aqua.harv.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.harv.dto.CustomerCreateRequest;
import com.evops.aqua.harv.entity.HarvCustomer;
import com.evops.aqua.harv.mapper.HarvCustomerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户档案：requestId 幂等登记，作为订单归属与通知接收方。
 */
@Service
public class HarvCustomerService {

    private final HarvCustomerMapper customerMapper;

    public HarvCustomerService(HarvCustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public HarvCustomer create(CustomerCreateRequest request) {
        HarvCustomer duplicated = customerMapper.selectOne(new QueryWrapper<HarvCustomer>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        HarvCustomer customer = new HarvCustomer();
        customer.setCustomerCode(request.getCustomerCode());
        customer.setCustomerName(request.getCustomerName());
        customer.setContactPhone(request.getContactPhone());
        customer.setRequestId(request.getRequestId());
        customerMapper.insert(customer);
        return customer;
    }

    public HarvCustomer getOrThrow(Long customerId) {
        HarvCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("客户不存在，id=" + customerId);
        }
        return customer;
    }

    public List<HarvCustomer> list() {
        return customerMapper.selectList(new QueryWrapper<HarvCustomer>().orderByAsc("id"));
    }
}
