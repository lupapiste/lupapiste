LUPAPISTE.OrganizationReportsModel = function() {
  "use strict";

  var self = this;

  self.startDate = ko.observable(moment().subtract(1, "months").toDate());
  self.endDate = ko.observable(new Date());
  self.startComputed = ko.pureComputed(function() {
    return moment(self.startDate()).startOf("day").valueOf();
  });
  self.endComputed = ko.pureComputed(function() {
    return moment(self.endDate()).endOf("day").valueOf();
  });

  self.partiesStartDate = ko.observable(moment().subtract(1, "months").toDate());
  self.partiesEndDate = ko.observable(new Date());
  self.partiesStartComputed = ko.pureComputed(function() {
    return moment(self.partiesStartDate()).startOf("day").valueOf();
  });
  self.partiesEndComputed = ko.pureComputed(function() {
    return moment(self.partiesEndDate()).endOf("day").valueOf();
  });

  self.verdictsStartDate = ko.observable(moment().subtract(1, "months").toDate());
  self.verdictsEndDate = ko.observable(new Date());
  self.verdictsStartComputed = ko.pureComputed(function() {
      return moment(self.verdictsStartDate()).startOf("day").valueOf();
  });
  self.verdictsEndComputed = ko.pureComputed(function() {
      return moment(self.verdictsEndDate()).endOf("day").valueOf();
  });

  self.storeBillingStartDate = ko.observable(moment().subtract(1, "months").toDate());
  self.storeBillingEndDate = ko.observable(new Date());
  self.storeBillingStartComputed = ko.pureComputed(function() {
    return moment(self.storeBillingStartDate()).startOf("day").valueOf();
  });
  self.storeBillingEndComputed = ko.pureComputed(function() {
    return moment(self.storeBillingEndDate()).endOf("day").valueOf();
  });

};
