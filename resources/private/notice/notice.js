LUPAPISTE.TagsDataProvider = function(organization, filtered) {
  "use strict";
  var self = this;

  self.query = ko.observable();

  self.filtered = ko.observableArray(filtered);

  var data = ko.observable();

  if (organization && util.getIn(lupapisteApp.models.currentUser, ["orgAuthz", organization])) {
    ajax
      .query("get-organization-tags", {organizationId: organization})
      .error(_.noop)
      .success(function(res) {
        data(res.tags);
      })
      .call();
  }

  self.data = ko.pureComputed(function() {
    var filteredData = _.filter(data(), function(item) {
      return !_.includes(self.filtered(), item);
    });
    var q = self.query() || "";
    filteredData = _.filter(filteredData, function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
    return filteredData;
  });
};

LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;
  self.authorityNotice = ko.observable();
  self.urgency = ko.observable("normal");

  self.indicator = ko.observable({name: undefined, type: undefined}).extend({notify: "always"});

  self.availableUrgencyStates = ko.observableArray(["normal", "urgent", "pending"]);

  self.selectedTags = ko.observableArray([]);

  self.applicationTagsProvider = new LUPAPISTE.TagsDataProvider(self.applicationId, self.selectedTags());

  var subscriptions = [];

  var subscribe = function() {
    subscriptions.push(self.urgency.subscribe(_.debounce(function(value) {
      ajax
        .command("change-urgency", {
          id: self.applicationId,
          urgency: value})
        .success(function() {
          self.indicator({name: "urgency", type: "saved"});
        })
        .error(function() {
          self.indicator({name: "urgency", type: "err"});
        })
        .call();
    }, 500)));

    subscriptions.push(self.authorityNotice.subscribe(_.debounce(function(value) {
      ajax
        .command("add-authority-notice", {
          id: self.applicationId,
          authorityNotice: value})
        .success(function() {
          self.indicator({name: "notice", type: "saved"});
        })
        .error(function() {
          self.indicator({name: "urgency", type: "err"});
        })
        .call();
    }, 500)));

    subscriptions.push(self.selectedTags.subscribe(_.debounce(function(tags) {
      ajax
        .command("add-application-tags", {
          id: self.applicationId,
          tags: tags})
        .success(function() {
          self.indicator({name: "tags", type: "saved"});
        })
        .error(function() {
          self.indicator({name: "tags", type: "err"});
        })
        .call();
      self.applicationTagsProvider.filtered(tags);
    }, 500)));
  };

  var unsubscribe = function() {
    while(subscriptions.length !== 0) {
      subscriptions.pop().dispose();
    }
  };

  subscribe();

  self.refresh = function(application) {
    // unsubscribe so that refresh does not trigger save
    unsubscribe();
    self.applicationId = application.id;
    self.urgency(application.urgency);
    self.authorityNotice(application.authorityNotice);
    self.selectedTags(application.tags ? application.tags : []);
    self.applicationTagsProvider = new LUPAPISTE.TagsDataProvider(application.organization, self.selectedTags());
    subscribe();
  };
};
