LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;
  console.log("parammss", params);

  self.dataProvider = params.dataProvider;
  self.data = self.dataProvider.data;

};