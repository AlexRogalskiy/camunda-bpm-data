package io.holunda.camunda.bpm.data.example.kotlin.process

import io.holunda.camunda.bpm.data.CamundaBpmData.*
import io.holunda.camunda.bpm.data.example.kotlin.domain.Order
import io.holunda.camunda.bpm.data.example.kotlin.domain.OrderPosition
import io.holunda.camunda.bpm.data.example.kotlin.process.OrderApproval.Variables.ORDER
import io.holunda.camunda.bpm.data.example.kotlin.process.OrderApproval.Variables.ORDER_ID
import io.holunda.camunda.bpm.data.example.kotlin.process.OrderApproval.Variables.ORDER_POSITION
import io.holunda.camunda.bpm.data.example.kotlin.process.OrderApproval.Variables.ORDER_TOTAL
import io.holunda.camunda.bpm.data.example.kotlin.service.OrderRepository
import mu.KLogging
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.DelegateTask
import org.camunda.bpm.engine.delegate.ExecutionListener
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import java.math.BigDecimal

@Configuration
class OrderApproval {

  @Autowired
  lateinit var orderRepository: OrderRepository

  companion object : KLogging() {
    const val KEY = "order-approval"
  }

  object Variables {
    val ORDER_ID = stringVariable("orderId")
    val ORDER = customVariable("order", Order::class.java)
    val ORDER_APPROVED = booleanVariable("orderApproved")
    val ORDER_POSITION = customVariable("orderPosition", OrderPosition::class.java)
    val ORDER_TOTAL = customVariable("orderTotal", BigDecimal::class.java)
  }

  /**
   * Load a primitive variable by id (string) and store a complex variable (order).
   */
  @Bean
  fun loadOrder() = JavaDelegate { execution ->
    val orderId = ORDER_ID.from(execution).get()
    val order = orderRepository.loadOrder(orderId)
    ORDER.on(execution).set(order)
  }

  /**
   * Load a local order position, write a local variable.
   */
  @Bean
  fun calculateOrderPositions() = JavaDelegate { execution ->
    val orderPosition = ORDER_POSITION.from(execution).get()
    val oldTotal = ORDER_TOTAL.from(execution).optional.orElse(BigDecimal.ZERO)
    val newTotal = oldTotal.plus(orderPosition.netCost.times(BigDecimal.valueOf(orderPosition.amount)))
    ORDER_TOTAL.on(execution).setLocal(newTotal)
  }

  /**
   * Read a local variable and store it in global variable.
   */
  @Bean
  fun writeOrderTotal() = ExecutionListener { execution ->
    val total = ORDER_TOTAL.from(execution).local
    ORDER_TOTAL.on(execution).set(total)
  }

  /**
   * Log the task id.
   */
  @EventListener(condition = "#task != null && #task.eventName == 'create'")
  fun taskLogger(task: DelegateTask) {
    logger.info("TASK LOGGER: Created user task ${task.id}")
  }

  @EventListener(condition = "#execution != null && #execution.eventName == 'start' && #execution.currentActivityId == 'start_order_created'")
  fun processStartLogger(execution: DelegateExecution) {
    logger.info { "INSTANCE LOGGER: Started process instance ${execution.processInstanceId}" }
  }

  @EventListener(condition = "#execution != null && #execution.eventName == 'end' && #execution.currentActivityId == 'end_order_approved'")
  fun processEndLogger(execution: DelegateExecution) {
    logger.info { "INSTANCE LOGGER: Finished process instance ${execution.processInstanceId}" }
  }

}
