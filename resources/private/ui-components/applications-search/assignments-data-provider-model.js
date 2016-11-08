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
    {field: ko.observable("created"), asc: ko.observable(true)};
  self.data             = ko.observable(params.data || defaultData);
  self.results          = ko.observable(self.data().searchResults);
  self.limit            = params.currentLimit;
  self.skip             = ko.observable(0);
  self.searchResultType = ko.observable(params.searchResultType);

  self.pending = ko.observable(false);

  var stateClasses = {
    created: "lupicon-circle-attention",
    completed: "lupicon-circle-check"
  };

  function enrichAssignmentData(assignment) {
    var createdState = _.find(assignment.states, function(state) { return state.type === "created"; });
    var currentState = _.maxBy(assignment.states, "timestamp");
    var completed = (currentState.type === "completed");
    return _.merge(assignment, {
      currentState: currentState,
      createdState: createdState,
      incomplete: !completed,
      creatorName: createdState.user.firstName + " " + createdState.user.lastName,
      statusClass: stateClasses[currentState.type],
      addressAndMunicipality: assignment.application.address + ", " + loc(["municipality", assignment.application.municipality]),
      targetGroup: loc("application.assignment.type." + assignment.target.group),
      targetType: loc(assignment.target["type-key"]),
      targetInfo: assignment.target["info-key"] && loc(assignment.target["info-key"]) || assignment.target.description
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

  self.searchStartDate = ko.observable();
  self.searchEndDate = ko.observable();

  function recipientSearchCond(selected, myid) {
    if (_.includes(selected, lupapisteApp.services.assignmentRecipientFilterService.myown)) {
      return [myid];
    } else if (_.includes(selected, lupapisteApp.services.assignmentRecipientFilterService.all)) {
      return [];
    } else {
      return _.map(selected, "id");
    }
  }

  var searchFields = ko.pureComputed(function() {
    var myid = lupapisteApp.models.currentUser.id();
    if (myid == null) {
      return;
    }

    // Datepicker returns a timestamp representing the selected date at midnight local time
    // search end date must be adjusted by one full day forward to include the end date as a whole into the search range
    var searchStartDateInMillis = self.searchStartDate() ? new Date(self.searchStartDate()).getTime() : null;
    var searchEndDateInMillis = self.searchEndDate() ? moment(new Date(self.searchEndDate()).getTime()).add(1, "days").valueOf() : null;

    return {
      searchText: self.searchFieldDelayed(),
      state: self.searchResultType(),
      recipient: recipientSearchCond(lupapisteApp.services.assignmentRecipientFilterService.selected(), myid),
      operation: _.map(lupapisteApp.services.operationFilterService.selected(), "id"),
      limit: self.limit(),
      area: _.map(lupapisteApp.services.areaFilterService.selected(), "id"),
      createdDate: {start: searchStartDateInMillis, end: searchEndDateInMillis},
      targetType: _.map(lupapisteApp.services.assignmentTargetFilterService.selected(), "id"),
      sort: ko.mapping.toJS(self.sort),
      skip: self.skip()
    };
  });

  function loadAssignments() {
    if (pageutil.getPage() === "applications") {
      ajax.datatables("assignments-search", searchFields())
      .success(self.onSuccess)
      .onError("error.unauthorized", notify.ajaxError)
      .pending(self.pending)
      .call();
    }
  }

  hub.onPageLoad("applications", loadAssignments);

  hub.subscribe("assignmentService::assignmentCompleted", loadAssignments);

  ko.computed(loadAssignments).extend({rateLimit: 0}); // http://knockoutjs.com/documentation/rateLimit-observable.html#example-3-avoiding-multiple-ajax-requests
};
