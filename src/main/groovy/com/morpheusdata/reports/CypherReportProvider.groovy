package com.morpheusdata.reports

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportType
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.response.ServiceResponse
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import io.reactivex.Observable;
import java.sql.Connection

@Slf4j
class CypherReportProvider extends AbstractReportProvider {
	Plugin plugin
	MorpheusContext morpheusContext

	CypherReportProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override
	MorpheusContext getMorpheus() {
		morpheusContext
	}

	@Override
	Plugin getPlugin() {
		plugin
	}

	@Override
	String getCode() {
		'cypher-report'
	}

	@Override
	String getName() {
		'Cypher Summary'
	}

	 ServiceResponse validateOptions(Map opts) {
		 return ServiceResponse.success()
	 }


	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<String> model = new ViewModel<String>()
		model.object = reportRowsBySection
		getRenderer().renderTemplate("hbs/cypherReport", model)
	}

	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp
	}

	void process(ReportResult reportResult) {
		// Update the status of the report (generating) - https://developer.morpheusdata.com/api/com/morpheusdata/model/ReportResult.Status.html
		morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingGet();
		Long displayOrder = 0
		List<GroovyRowResult> results = []
		Connection dbConnection
		Long passwordResults = 0
		Long tfvarsResults = 0
		Long secretResults = 0
		Long uuidResults = 0
		Long keyResults = 0
		Long randomResults = 0
		Long totalItems = 0

		try {
			// Create a read-only database connection
			dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
			// Query the cypher_item table for all items
			results = new Sql(dbConnection).rows("SELECT item_key,last_updated,last_accessed,lease_timeout from cypher_item order by item_key asc;")
		} finally {
			// Close the database connection
			morpheus.report.releaseDatabaseConnection(dbConnection)
		}
		log.info("Results: ${results}")
		Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
		observable.map{ resultRow ->
			log.info("Mapping resultRow ${resultRow}")
			Map<String,Object> data = [key: resultRow.item_key, last_updated: resultRow.last_updated.toString(), last_accessed: resultRow.last_accessed.toString(), lease_timeout: resultRow.lease_timeout ]
			ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
			log.info("resultRowRecord: ${resultRowRecord.dump()}")
			
			// Increment the total cypher items count by 1
			totalItems++

			// Evaluate if the item_key column starts with password
			// and increment the password results count by 1
			if (resultRow.item_key.startsWith('password')) {
				passwordResults++
			}

			// Evaluate if the item_key column starts with tfvars
			// and increment the tfvars results count by 1
			if (resultRow.item_key.startsWith('tfvars')) {
				tfvarsResults++
			}

			// Evaluate if the item_key column starts with secret
			// and increment the secret results count by 1
			if (resultRow.item_key.startsWith('secret')) {
				secretResults++
			}

			// Evaluate if the item_key column starts with uuid
			// and increment the uuid results count by 1
			if (resultRow.item_key.startsWith('uuid')) {
				uuidResults++
			}

			// Evaluate if the item_key column starts with key
			// and increment the key results count by 1
			if (resultRow.item_key.startsWith('key')) {
				keyResults++
			}

			// Evaluate if the item_key column starts with random
			// and increment the random results count by 1
			if (resultRow.item_key.startsWith('random')) {
				randomResults++
			}


			return resultRowRecord
		}.buffer(50).doOnComplete {
			// Update the report status to "ready" upon successfully interating through all the records
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
		}.doOnError { Throwable t ->
			// Update the report status to "failed" if there is an error in interating through all the records
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingGet();
		}.subscribe {resultRows ->
			// Append the resultRowRecord to the report data payload
			morpheus.report.appendResultRows(reportResult,resultRows).blockingGet()
		}

		// Create a data map to hold the header result data
		Map<String,Object> headerData = [total_items: totalItems, password_items: passwordResults, tfvars_items: tfvarsResults, secret_items: secretResults, uuid_items: uuidResults, key_items: keyResults, random_items: randomResults]

		// Create a new result row record for the header using the data from the headerData
		ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_HEADER, displayOrder: displayOrder++, dataMap: headerData)

		// Append the header resultRowRecord to the report data
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()
	}

	// https://developer.morpheusdata.com/api/com/morpheusdata/core/ReportProvider.html#method.summary
	// The description associated with the custom report
	 @Override
	 String getDescription() {
		 return "View an inventory of Cypher items"
	 }

	// The category of the custom report
	 @Override
	 String getCategory() {
		 return 'inventory'
	 }

	 @Override
	 Boolean getOwnerOnly() {
		 return false
	 }

	 @Override
	 Boolean getMasterOnly() {
		 return true
	 }

	 @Override
	 Boolean getSupportsAllZoneTypes() {
		 return true
	 }

	// https://developer.morpheusdata.com/api/com/morpheusdata/model/OptionType.html
	 @Override
	 List<OptionType> getOptionTypes() {}
 }