LUPAPISTE.TagsDataProvider = function(applicationId, filtered) {
  var self = this;

  self.query = ko.observable();

  self.filtered = ko.observableArray(_.map(filtered, function(i) { return i.label; }));

  var data = ko.observable();

  ajax
    .query("available-application-tags", {id: applicationId})
    .success(function(res) {
      data(res.tags);
    })
    .call();

  self.data = ko.pureComputed(function() {
    var filteredData = _.filter(data(), function(item) {
      return !_.includes(self.filtered(), item);
    });
    var mappedData = _.map(filteredData, function(item) {
      return {label: item};
    });
    console.log("mappedData", mappedData);
    return mappedData;
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

  self.selectedTags = ko.observableArray();

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

    subscriptions.push(self.selectedTags.subscribe(_.debounce(function(value) {
      var tags = _.map(value, function(i) { return i.label; });
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
    console.log("refresh");
    // unsubscribe so that refresh does not trigger save
    unsubscribe();
    self.applicationId = application.id;
    self.urgency(application.urgency);
    self.authorityNotice(application.authorityNotice);
    self.selectedTags(_.map(application.tags, function(item) { return {label: item}; }));
    self.applicationTagsProvider = new LUPAPISTE.TagsDataProvider(application.id, self.selectedTags());
    // self.applicationTagsProvider.filtered(_.map(self.selectedTags(), function(i) { return i.label; }));
    subscribe();
  };
};
