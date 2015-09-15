LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;


  self.dataProvider = params.dataProvider;
  self.data = self.dataProvider.applications;
  self.gotResults = params.gotResults;

  self.selectedTab = self.dataProvider.applicationType;

  self.sortBy = function(target) {
    self.dataProvider.skip(0);
    var sortObj = self.dataProvider.sort;
    if ( target === sortObj.field() ) {
      sortObj.asc(!sortObj.asc()); // toggle direction
    } else {
      sortObj.field(target);
      sortObj.asc(false);
    }
  };

  self.offset = 0;
  self.onPageLoad = hub.onPageLoad(pageutil.getPage(), function() {
    // Offset is not supported in IE8
    if (self.offset) {
      window.scrollTo(0, self.offset);
    }
  });

  self.openApplication = function(model, event, target) {
    self.offset = window.pageYOffset;
    pageutil.openApplicationPage(model, target);
  };

  self.dispose = _.partial(hub.unsubscribe, self.onPageLoad);

};
