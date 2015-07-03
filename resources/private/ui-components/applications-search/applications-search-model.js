LUPAPISTE.ApplicationsDataProvider = function() {
  "use strict";

  var self = this;

  self.data = ko.observableArray([]);

  self.searchField = ko.observable("");

  self.handler = ko.observable();

  ko.computed(function() {
    ajax.query("applications-search",
               {searchText: self.searchField()})
      .success(function(data) {
        console.log(data);
        self.data(data.data);
    })
    .call();
  }).extend({throttle: 250});

};

LUPAPISTE.ApplicationsSearchModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = new LUPAPISTE.ApplicationsDataProvider();
};
