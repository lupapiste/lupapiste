LUPAPISTE.AssignmentsDataProvider = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var defaultData = {
    searchResults: [],
    totalCount: -1,
    userTotalCount: -1
  };

  self.sort = params.sort ||
    {field: ko.observable("description"), asc: ko.observable(true)};
  self.data             = ko.observable(params.data || defaultData);
  self.results          = ko.observable(self.data().searchResults);
  self.limit            = params.currentLimit;
  self.skip             = ko.observable(0);
  self.searchResultType = ko.observable(params.searchResultType);

  self.pending = ko.observable(false);

  var statusClasses = {
    active: "lupicon-circle-attention",
    completed: "lupicon-circle-check"
  };

  function enrichAssignmentData(assignment) {
    return _.merge(assignment, {
      creatorName: assignment.creator.firstName + " " + assignment.creator.lastName,
      statusClass: statusClasses[assignment.status],
      addressAndMunicipality: assignment.application.address + ", " + loc(["municipality", assignment.application.municipality])
    });
  }

  self.onSuccess = function(res) {
    var assignments = _.map(res.data.assignments, enrichAssignmentData);
    self.data({searchResults: assignments,
               totalCount: res.data.totalCount,
               userTotalCount: res.data.userTotalCount});
    self.results(assignments);
  };

  self.searchField = ko.observable("");
  self.searchFieldDelayed = ko.pureComputed(self.searchField)
    .extend({rateLimit: {method: "notifyWhenChangesStop", timeout: 750}});

  var searchFields = ko.pureComputed(function() {
    return {
      searchText: self.searchFieldDelayed(),
      status: self.searchResultType(),
      recipient: lupapisteApp.models.currentUser.username(),
      limit: self.limit(),
      skip: self.skip()
    };
  });

  function loadAssignments() {
    ajax.datatables("assignments-search", searchFields())
      .success(self.onSuccess)
      .onError("error.unauthorized", notify.ajaxError)
      .pending(self.pending)
      .call();
  }

  hub.onPageLoad("applications", loadAssignments);

  hub.subscribe("assignmentService::assignmentCompleted", loadAssignments);

  ko.computed(loadAssignments).extend({rateLimit: 0}); // http://knockoutjs.com/documentation/rateLimit-observable.html#example-3-avoiding-multiple-ajax-requests
};
