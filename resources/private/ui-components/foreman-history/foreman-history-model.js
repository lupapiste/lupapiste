LUPAPISTE.ForemanHistoryModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.showAll = ko.observable();
  self.allVisible = ko.observable();

  self.params = params;
  self.projects = ko.observableArray([]);

  self.disposedComputed( function() {
      ajax
      .query("foreman-history", {id: params.applicationId,
                                 all: Boolean(self.showAll())})
      .success(function (res) {
        self.projects(res.projects);
        self.allVisible( res.all );
      })
      .call();
  });
};
