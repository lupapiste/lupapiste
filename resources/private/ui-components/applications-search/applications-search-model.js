LUPAPISTE.ApplicationsDataProvider = function() {
  "use strict";

  var self = this;

  self.data = ko.observableArray([]);

  self.applicationType = ko.observable();

  self.searchField = ko.observable("");

  self.handler = ko.observable();

  self.applicationTags = ko.observableArray([]);

  ko.computed(function() {
    ajax.query("applications-search",
               {searchText: self.searchField(),
                applicationTags: self.applicationTags(),
                handler: self.handler() ? self.handler().id : undefined,
                applicationType: self.applicationType()})
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
