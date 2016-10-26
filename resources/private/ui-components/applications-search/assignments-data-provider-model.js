LUPAPISTE.AssignmentsDataProvider = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var defaultData = {
    searchResults: [],
    totalCount: -1,
    userTotalCount: -1
  };

  self.sort    = params.sort ||
    {field: ko.observable("description"), asc: ko.observable(true)};
  self.data    = ko.observable(params.data || defaultData);
  self.results = ko.observable(self.data().searchResults);
  self.limit   = ko.observable(params.limit);
  self.skip    = ko.observable(0);

  self.pending = ko.observable(false);

  var statusClasses = {
    active: "lupicon-circle-attention",
    completed: "lupicon-circle-check"
  };

  function enrichAssignmentData(assignment) {
    return _.merge(assignment, {
      creatorName: assignment.creator.firstName + " " + assignment.creator.lastName,
      statusClass: statusClasses[assignment.status],
      addressAndMunicipality: assignment.application.address + ", " + loc(['municipality', assignment.application.municipality])
    });
  }

  self.onSuccess = function(res) {
    var assignments = _.map(res.assignments, enrichAssignmentData);
    self.data({searchResults: assignments,
               totalCount: assignments.length,
               userTotalCount: assignments.length});
    self.results(assignments);
  };

  self.searchField = ko.observable("");
  var searchFields = ko.pureComputed(function() {
    searchText: self.searchField()
  });
  hub.onPageLoad("applications", function() {
    ajax.datatables("assignments", searchFields())
      .success(self.onSuccess)
      .onError("error.unauthorized", notify.ajaxError)
      .pending(self.pending)
      .call();
  });

  ko.computed(function() {
    ajax.datatables("assignments", searchFields())
      .success(self.onSuccess)
      .onError("error.unauthorized", notify.ajaxError)
      .pending(self.pending)
      .call();
  }).extend({rateLimit: 0}); // http://knockoutjs.com/documentation/rateLimit-observable.html#example-3-avoiding-multiple-ajax-requests
};
