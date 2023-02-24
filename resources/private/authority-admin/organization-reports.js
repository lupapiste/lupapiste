LUPAPISTE.OrganizationReportsModel = function() {
  "use strict";

  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  self.startDate = ko.observable(moment().subtract(1, "months").toDate());
  self.endDate = ko.observable(new Date());
  self.startComputed = self.disposedPureComputed(function() {
    return moment(self.startDate()).startOf("day").valueOf();
  });
  self.endComputed = self.disposedPureComputed(function() {
    return moment(self.endDate()).endOf("day").valueOf();
  });

  self.partiesStartDate = ko.observable(moment().subtract(1, "months").toDate());
  self.partiesEndDate = ko.observable(new Date());
  self.partiesStartComputed = self.disposedPureComputed(function() {
    return moment(self.partiesStartDate()).startOf("day").valueOf();
  });
  self.partiesEndComputed = self.disposedPureComputed(function() {
    return moment(self.partiesEndDate()).endOf("day").valueOf();
  });

  self.verdictsStartDate = ko.observable(moment().subtract(1, "months").toDate());
  self.verdictsEndDate = ko.observable(new Date());
  self.verdictsStartComputed = self.disposedPureComputed(function() {
      return moment(self.verdictsStartDate()).startOf("day").valueOf();
  });
  self.verdictsEndComputed = self.disposedPureComputed(function() {
      return moment(self.verdictsEndDate()).endOf("day").valueOf();
  });

  self.storeBillingStartDate = ko.observable(moment().startOf("month").toDate());
  self.storeBillingEndDate = ko.observable(new Date());
  self.storeBillingStartComputed = self.disposedPureComputed(function() {
    return moment(self.storeBillingStartDate()).startOf("day").valueOf();
  });
  self.storeBillingEndComputed = self.disposedPureComputed(function() {
    return moment(self.storeBillingEndDate()).endOf("day").valueOf();
  });

  self.storeDownloadsStartDate = ko.observable(moment().startOf("month").toDate());
  self.storeDownloadsEndDate = ko.observable(new Date());
  self.storeDownloadsStartComputed = self.disposedPureComputed(function() {
    return moment(self.storeDownloadsStartDate()).startOf("day").valueOf();
  });
  self.storeDownloadsEndComputed = self.disposedPureComputed(function() {
    return moment(self.storeDownloadsEndDate()).endOf("day").valueOf();
  });


  self.invoicesReportStartDate = ko.observable(moment().startOf("month").toDate());
  self.invoicesReportEndDate = ko.observable(new Date());
  self.invoicesReportStartComputed = self.disposedPureComputed(function() {
    return moment(self.invoicesReportStartDate()).startOf("day").valueOf();
  });
  self.invoicesReportEndComputed = self.disposedPureComputed(function() {
    return moment(self.invoicesReportEndDate()).endOf("day").valueOf();
  });

  self.lastMonthStart = ko.observable(moment().subtract(1, "months").startOf("month").startOf("day").valueOf());
  self.lastMonthEnd = ko.observable(moment().subtract(1, "months").endOf("month").endOf("day").valueOf());

  self.archivalStartDate = ko.observable(moment().subtract(1, "years").toDate());
  self.archivalEndDate = ko.observable(new Date());
  self.archivalStartComputed = self.disposedPureComputed(function() {
    return moment(self.archivalStartDate()).startOf("day").valueOf();
  });
  self.archivalEndComputed = self.disposedPureComputed(function() {
    return moment(self.archivalEndDate()).endOf("day").valueOf();
  });

  self.onkaloStartDate = ko.observable(moment().subtract(1, "years").toDate());
  self.onkaloEndDate = ko.observable(new Date());
  self.onkaloStartComputed = self.disposedPureComputed(function() {
    return moment(self.onkaloStartDate()).startOf("day").valueOf();
  });
  self.onkaloEndComputed = self.disposedPureComputed(function() {
    return moment(self.onkaloEndDate()).endOf("day").valueOf();
  });

  self.organizationId = self.disposedPureComputed( function() {
    return lupapisteApp.usagePurpose().orgId;
    }
                                                 ) ;
  self.wasteReportYear = ko.observable( (new Date()).getFullYear() );

  self.badYear = self.disposedPureComputed( function() {
    return !/^\s*\d+\s*$/.test( self.wasteReportYear() );
  });

};
