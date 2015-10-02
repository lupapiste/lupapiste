LUPAPISTE.ApplicationsForemanSearchResultsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchResultsModel(params));

  self.columns = [
    self.createColumn("first", "applications.indicators", {colspan: lupapisteApp.models.currentUser.isAuthority() ? "4" : "3", sortable: false}),
    self.createColumn("second", "applications.id", {sortField: "id"}),
    self.createColumn("third", "applications.type", {sortField: "type"}),
    self.createColumn("fourth", "applications.location", {sortField: "location"}),
    self.createColumn("fifth", "applications.foreman-name", {sortField: "foreman"}),
    self.createColumn("sixth", "applications.foreman-role", {sortField: "foremanRole"}),
    self.createColumn("seventh", "applications.sent", {sortField: "submitted"}),
    self.createColumn("eight", "applications.status", {sortField: "state"})
  ];
};
