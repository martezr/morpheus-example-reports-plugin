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
import java.util.Date
import java.util.concurrent.TimeUnit
import groovy.json.*

import java.sql.Connection

@Slf4j
class WorkloadUsageReportProvider extends AbstractReportProvider {
	Plugin plugin
	MorpheusContext morpheusContext

	WorkloadUsageReportProvider(Plugin plugin, MorpheusContext context) {
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
		'workload-usage-overview-report'
	}

	@Override
	String getName() {
		'Workload Usage Overview'
	}

	 ServiceResponse validateOptions(Map opts) {
		 return ServiceResponse.success()
	 }


	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<String> model = new ViewModel<String>()
		def HashMap<String, String> reportPayload = new HashMap<String, String>();

		// Add web nonce to allow the use of javascript scripts
		def webnonce = morpheus.getWebRequest().getNonceToken()
		reportPayload.put("webnonce",webnonce)

		// Pass report data to the hbs render
		reportPayload.put("reportdata",reportRowsBySection)
		model.object = reportPayload
		getRenderer().renderTemplate("hbs/workloadUsage", model)
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
		List<GroovyRowResult> instances = []
		List<GroovyRowResult> accounts = []
		List<GroovyRowResult> clouds = []
		Connection dbConnection
		Long totalWorkloads = 0
		Long licensedWorkloads = 0
		Long discoveredWorkloads = 0
		Long provisionedWorkloads = 0
		def accountpayload = []
		def cloudpayload = []
		def accountNames = []

		try {
			// Create a read-only database connection
			dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
			// Evaluate if a search filter or phrase has been defined
			results = new Sql(dbConnection).rows("SELECT compute_server.id,compute_server.name,compute_server.provision,compute_server.status,compute_server.server_type,compute_server.zone_id,compute_server.managed,compute_server.server_type,compute_server.status,compute_server.discovered,compute_server.account_id,compute_server.power_state,compute_server_type.name as server_type_name, compute_zone.name as cloud_name, account.name as account_name FROM compute_server LEFT JOIN account ON compute_server.account_id = account.id LEFT JOIN compute_zone on compute_server.zone_id = compute_zone.id INNER JOIN compute_server_type ON compute_server.compute_server_type_id=compute_server_type.id WHERE compute_server.compute_server_type_id in (select id from compute_server_type where managed = 0 and container_hypervisor = 0 and vm_hypervisor = 0 ) order by id asc;")
			instances = new Sql(dbConnection).rows("SELECT instance.id, instance.name, compute_zone.name as cloud_name, account.name as account_name FROM instance LEFT JOIN compute_zone on instance.provision_zone_id = compute_zone.id LEFT JOIN account ON instance.account_id = account.id order by id asc;")
			accounts = new Sql(dbConnection).rows("SELECT name from account order by id asc;")
			clouds = new Sql(dbConnection).rows("SELECT name from compute_zone order by id asc;")

/*
{
	[
		"name": "tenantA",
		"workloadCount": 34,
		"workloads": [
			{"name":"demo","cpu":"test"},
			{"name":"demo","cpu":"test"}
		]
	]
}
*/
			accounts.each {
				def payload = [:]
				payload["name"] = it.name
				payload["count"] = 0
				payload["workloads"] = []
				accountpayload << payload
			}
			clouds.each {
				def payload = [:]
				payload["name"] = it.name
				payload["count"] = 0
				cloudpayload << payload
			}
	    // Close the database connection
		} finally {
			morpheus.report.releaseDatabaseConnection(dbConnection)
		}
		Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
		observable.map{ resultRow ->
			Map<String,Object> data = [id: resultRow.id, name: resultRow.name, cloud_id: resultRow.zone_id, cloud_name: resultRow.cloud_name, account_name: resultRow.account_name, discovered: resultRow.discovered, type: resultRow.server_type_name, server_type: resultRow.server_type ]
			
			ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
			accountpayload.each {
				if (it.name == resultRow.account_name) {
					def payload = [:]
					payload["name"] = resultRow.name
					payload["cloud"] = resultRow.cloud_name
					payload["discovered"] = resultRow.discovered
					it.workloads << payload
					it.count++
				}
			}
			cloudpayload.each {
				if (it.name == resultRow.cloud_name) {
					it.count = it.count + 1
				}
			}
			totalWorkloads++
			switch(resultRow.discovered) {
				case false:
					provisionedWorkloads++
					break;
				case true:
					discoveredWorkloads++
					break;
			}
			return resultRowRecord
		}.buffer(50).doOnComplete {
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
		}.doOnError { Throwable t ->
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingGet();
		}.subscribe {resultRows ->
			morpheus.report.appendResultRows(reportResult,resultRows).blockingGet()
		}

		instances.each { instance ->
			accountpayload.each { account ->
				if (instance.account_name == account.name){
					account.count++
				}
			}
			cloudpayload.each { cloud ->
				if (instance.cloud_name == cloud.name){
					cloud.count++
				}
			}
		}

		def list = []
		def discoveredOutput = [name: "discovered", value: discoveredWorkloads, color: "#3366cc"]
		list << discoveredOutput
		def provisionedOutput = [name: "provisioned", value: provisionedWorkloads + instances.size(), color: "#dc3912"]
		list << provisionedOutput
		def json = JsonOutput.toJson(list)
		def accountsJson = JsonOutput.toJson(accountpayload)
		def sortedAccounts = accountpayload.sort { -it.count }
		sortedAccounts.each{
			accountNames << it.name
		}
		def firstFiveAccounts = []
		firstFiveAccounts << sortedAccounts[0]
		firstFiveAccounts << sortedAccounts[1]
		firstFiveAccounts << sortedAccounts[2]
		firstFiveAccounts << sortedAccounts[3]
		firstFiveAccounts << sortedAccounts[4]
		def sortedClouds = cloudpayload.sort { -it.count }
		def firstFiveClouds = []
		firstFiveClouds << sortedClouds[0]
		firstFiveClouds << sortedClouds[1]
		firstFiveClouds << sortedClouds[2]
		firstFiveClouds << sortedClouds[3]
		firstFiveClouds << sortedClouds[4]
		def cloudsJson = JsonOutput.toJson(sortedClouds)
		Map<String,Object> data = [totalWorkloads: totalWorkloads + instances.size(), licensedWorkloads: licensedWorkloads, discoveredJson: json, discoveredPayload: list, totalInstances: instances.size(), instancedata: instances, accountsJson: accountsJson, cloudsJson: cloudsJson, cloudsPayload: firstFiveClouds, accountsPayload: firstFiveAccounts, accountNames: accountNames, serverPayload: accountpayload ]
		ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_HEADER, displayOrder: displayOrder++, dataMap: data)
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()
	}

	// https://developer.morpheusdata.com/api/com/morpheusdata/core/ReportProvider.html#method.summary
	// The description associated with the custom report
	 @Override
	 String getDescription() {
		 return "Workload Usage Overview"
	 }

	// The category of the custom report
	 @Override
	 String getCategory() {
		 return 'Inventory'
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