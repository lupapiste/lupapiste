LUPAPISTE.AssignmentsSearchResultsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchResultsModel(params));

  self.results = self.dataProvider.results;

  self.openApplication = function(model, event, target) {
    ajax.query("application", {id: model.application.id, lang: loc.getCurrentLanguage()})
      .success(function(res) {
        self.offset = window.pageYOffset;
        pageutil.openApplicationPage(res.application, target);
      })
      .call();
  };

  self.markComplete = function(id) {
    return function() {
      hub.send("assignmentService::markComplete", {assignmentId: id});
    };
  };

  self.columns = [
    util.createSortableColumn("first",   "application.assignment.status",
                              {sortable: false}),
    util.createSortableColumn("second",  "applications.id",
                              {sortable: false}),
    util.createSortableColumn("third",  "applications.location",
                              {sortable: false}),
    util.createSortableColumn("fourth",  "application.assignment.subject",
                              {sortable: false}),
    util.createSortableColumn("fifth",   "common.description",
                              {sortable: false}),
    util.createSortableColumn("sixth", "application.assignment.creator",
                              {sortable: false}),
    util.createSortableColumn("seventh",   "common.created",
                              {sortable: true,
                               sortField: "created",
                               currentSort: self.dataProvider.sort}),
    util.createSortableColumn("eighth", null,    // Mark complete
                              {sortable: false})

  ];
};
