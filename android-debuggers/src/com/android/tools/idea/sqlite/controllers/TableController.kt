/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableViewListener
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ComparatorUtil.max

/**
 * Controller specialized in displaying rows and columns from a [SqliteResultSet].
 *
 * The ownership of the [SqliteResultSet] is transferred to the [TableController],
 * i.e. it is closed when [dispose] is called.
 *
 * The [SqliteResultSet] is not necessarily associated with a real table in the database, in those cases the [tableName] will be null.
 */
@UiThread
class TableController(
  parentDisposable: Disposable,
  private var rowBatchSize: Int = 50,
  private val view: TableView,
  private val tableName: String?,
  private val sqliteService: SqliteService,
  private val sqliteStatement: SqliteStatement,
  private val edtExecutor: FutureCallbackExecutor
) : Disposable {
  private val listener = TableViewListenerImpl()

  private lateinit var resultSet: SqliteResultSet
  private var orderBy: OrderBy? = null
  private var start = 0

  init {
    Disposer.register(parentDisposable, this)
  }

  fun setUp(): ListenableFuture<Unit> {

    view.startTableLoading()

    return edtExecutor.transform(sqliteService.executeQuery(sqliteStatement)) { newResultSet ->
      if (Disposer.isDisposed(this)) {
        newResultSet.dispose()
        throw ProcessCanceledException()
      }

      view.showPageSizeValue(rowBatchSize)
      view.addListener(listener)

      resultSet = newResultSet
      Disposer.register(this, newResultSet)

      fetchAndDisplayTableData()

      return@transform
    }
  }

  override fun dispose() {
    view.removeListener(listener)
  }

  private fun fetchAndDisplayTableData() {
    val fetchTableDataFuture = edtExecutor.transformAsync(resultSet.columns) { columns ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()
      view.showTableColumns(columns!!)

      updateDataAndButtons()
    }

    val futureCatching = handleFetchRowsError(fetchTableDataFuture)

    edtExecutor.finallySync(futureCatching) {
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()
      view.stopTableLoading()
    }
  }

  private fun updateDataAndButtons(): ListenableFuture<Unit> {
    view.setFetchPreviousRowsButtonState(false)
    view.setFetchNextRowsButtonState(false)

    return edtExecutor.transformAsync(fetchAndDisplayRows()) {
      edtExecutor.transform(resultSet.rowCount) { rowCount ->
        view.setFetchPreviousRowsButtonState(start > 0)
        view.setFetchNextRowsButtonState(start+rowBatchSize < rowCount)
        return@transform
      }
    }
  }

  /**
   * Fetches rows through the [SqliteResultSet].
   */
  private fun fetchAndDisplayRows() : ListenableFuture<Unit> {
    return edtExecutor.transformAsync(resultSet.getRowBatch(start, rowBatchSize)) { rows ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()

      if (rows!!.isEmpty()) {
        view.removeRows()
        Futures.immediateFuture(Unit)
      } else {
        view.removeRows()
        view.showTableRowBatch(rows)
        Futures.immediateFuture(Unit)
      }
    }
  }

  private fun handleFetchRowsError(future: ListenableFuture<Unit>): ListenableFuture<Any> {
    return edtExecutor.catching(future, Throwable::class.java) { error ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()

      val message = "Error retrieving rows ${if (tableName != null) "for table \"$tableName\"" else ""}"
      view.reportError(message, error)
    }
  }

  private inner class TableViewListenerImpl : TableViewListener {
    override fun toggleOrderByColumnInvoked(sqliteColumn: SqliteColumn) {
      if (orderBy != null && orderBy!!.column == sqliteColumn) {
        orderBy = OrderBy(sqliteColumn, !orderBy!!.asc)
      } else {
        orderBy = OrderBy(sqliteColumn, true)
      }

      val order = if (orderBy!!.asc) "ASC" else "DESC"
      val newQuery =
        "SELECT * FROM (${sqliteStatement.sqliteStatementText}) ORDER BY ${AndroidSqlLexer.getValidName(orderBy!!.column.name)} $order"

      view.startTableLoading()
      Disposer.dispose(resultSet)

      edtExecutor.transform(sqliteService.executeQuery(SqliteStatement(newQuery, sqliteStatement.parametersValues))) { newResultSet ->
        if (Disposer.isDisposed(this@TableController)) {
          newResultSet.dispose()
          throw ProcessCanceledException()
        }

        resultSet = newResultSet
        Disposer.register(this@TableController, newResultSet)

        start = 0
        fetchAndDisplayTableData()
      }
    }

    override fun rowCountChanged(rowCount: Int) {
      if (rowCount < 0) {
        view.reportError("Row count must be non-negative", null)
        return
      }

      rowBatchSize = rowCount

      val future = updateDataAndButtons()
      handleFetchRowsError(future)
    }

    override fun loadPreviousRowsInvoked() {
      start = max(0, start-rowBatchSize)

      val future = updateDataAndButtons()
      handleFetchRowsError(future)
    }

    override fun loadNextRowsInvoked() {
      start += rowBatchSize

      val future = updateDataAndButtons()
      handleFetchRowsError(future)
    }

    override fun loadFirstRowsInvoked() {
      start = 0

      val future = updateDataAndButtons()
      handleFetchRowsError(future)
    }

    override fun loadLastRowsInvoked() {
      edtExecutor.transformAsync(resultSet.rowCount) { rowCount ->
        start = (rowCount!! / rowBatchSize) * rowBatchSize

        if (start == rowCount) start -= rowBatchSize

        val future = updateDataAndButtons()
        handleFetchRowsError(future)
      }
    }
  }
}

data class OrderBy(val column: SqliteColumn, val asc: Boolean)