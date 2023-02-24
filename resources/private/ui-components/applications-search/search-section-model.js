LUPAPISTE.SearchSectionModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel()) ;

  self.type = params.type;
  self.label = params.label;
  self.lLabel = params.lLabel;
  self.dataProvider = params.dataProvider;

  self.totalCount = ko.pureComputed(function() {
    return self.dataProvider.totalCount();
  });

  self.userTotalCount = ko.pureComputed(function() {
    return self.dataProvider.data().userTotalCount;
  });

  self.gotResults = ko.pureComputed(function() {
    return self.dataProvider.hasResults();
  });

  self.filter = {
    name: params.filterComponent,
    params: {
      dataProvider: self.dataProvider,
      gotResults: self.gotResults,
      externalApi: params.externalApi
    }
  };
  self.searchResults = {
    name: params.resultsComponent,
    params: {
      dataProvider: self.dataProvider,
      gotResults: self.gotResults
    }
  };
  self.paging = {
    name: params.pagingComponent,
    params: {
      dataProvider: self.dataProvider,
      limits: params.limits,
      resultsTextKey: params.resultsTextKey
    }
  };
  self.tabs = {
    name: params.tabsComponent,
    params: {
      dataProvider: self.dataProvider,
      gotResults: self.gotResults
    }
  };

  self.showMap = self.disposedPureComputed( function() {
    return self.dataProvider.mapSupported && self.dataProvider.mapView();
  });

};
