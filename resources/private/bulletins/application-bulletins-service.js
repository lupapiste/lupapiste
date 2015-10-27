LUPAPISTE.ApplicationBulletinsService = function() {
  "use strict";
  var self = this;

  self.data = ko.observableArray([]);
  self.bulletinsLeft = ko.observable(0);

  self.fetchBulletins = _.debounce(function (query) {
    // TODO setting these in service kind of defeats the purpose of sending events through hub
    self.currentSort.field(query.sort.field);
    self.currentSort.asc(query.sort.asc);
    self.currentPage(query.page);

    ajax.datatables("application-bulletins", query)
      .success(function(res) {
        self.bulletinsLeft(res.left);
        if (query.page === 1) {
          self.data(res.data);
        } else {
          self.data(self.data().concat(res.data));
        }
      })
      .pending(query.pending || _.noop)
      .call();
  }, 250);

  self.municipalities = ko.observableArray([]);
  self.fetchMunicipalities = function() {
    ajax.query("application-bulletin-municipalities", {})
      .success(function(res) {
        self.municipalities(res.municipalities);
      })
      .call();
  };

  self.states = ko.observableArray([]);
  self.fetchStates = function() {
    ajax.query("application-bulletin-states", {})
      .success(function(res) {
        self.states(res.states);
      })
      .call();
  };

  self.currentSort = {
    field: ko.observable("modified"),
    asc: ko.observable(false)
  };

  self.currentPage = ko.observable(1);

  var lastQuery = {
    page: 1,
    searchText: "",
    municipality: "",
    state: "",
    sort: {field: "modified", asc: false}
  };

  hub.subscribe("bulletinService::fetchBulletins", function(event) {
    // Merge new partial query with last whole query
    _.merge(lastQuery, event, function(a,b) {
      if (_.isUndefined(b) || _.isNull(b)) {
        return "";
      }
      return b;
    });

    self.fetchBulletins(lastQuery);
  });

  hub.subscribe("bulletinService::fetchMunicipalities", function() {
    self.fetchMunicipalities();
  });

  hub.subscribe("bulletinService::fetchStates", function() {
    self.fetchStates();
  });
};

