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
};
