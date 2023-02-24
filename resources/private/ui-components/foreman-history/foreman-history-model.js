LUPAPISTE.ForemanHistoryModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.showAll = ko.observable();
  self.showCheck = ko.observable( true );

  self.params = params;
  self.projects = ko.observableArray([]);

  self.disposedComputed( function() {
      ajax.query("foreman-history",
                 {id: params.applicationId,
                  all: Boolean(self.showAll())})
      .success(function (res) {
        self.projects(res.projects);
        if( !self.showAll()) {
          self.showCheck( !res.all );
        }        
      })
      .call();
  });
  self.title = self.disposedPureComputed( function () {
    return  "tyonjohtaja.historia.otsikko"
         + (self.showAll() || !self.showCheck() ? "-kaikki" : "");
  });
  self.titleTestId = self.disposedPureComputed( function() {
    return _.replaceAll( self.title(), "\\.", "-");
  });
};
