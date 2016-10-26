LUPAPISTE.AssignmentsSearchResultsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchResultsModel(params));

  self.data = ko.pureComputed(self.dataProvider.results);

  self.openApplication = function(model, event, target) {
    console.log(model);
    ajax.query("application", {id: model.application.id, lang: loc.getCurrentLanguage()})
      .success(function(res) {
        self.offset = window.pageYOffset;
        pageutil.openApplicationPage(res.application, target);
      })
      .call();
  };

  self.columns = [
    util.createSortableColumn("first", "application.assignment.status",
                              {sortable: false}),
    util.createSortableColumn("second",  "applications.id",
                              {sortable: false}),
    util.createSortableColumn("third",  "applications.location",
                              {sortable: false}),
    util.createSortableColumn("fourth",   "application.assignment.subject",
                              {sortable: false}),
    util.createSortableColumn("fifth",   "",
                              {sortable: false}),
    util.createSortableColumn("sixth",   "application.assignment.creator",
                              {sortable: false})
  ];
};
