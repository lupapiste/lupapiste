LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;
  self.authorityNotice = ko.observable();
  self.urgency = ko.observable('normal');

  self.indicator = ko.observable().extend({notify: 'always'});

  self.availableUrgencyStates = ko.observableArray(['normal', 'urgent', 'pending']);

  var subscriptions = [];

  var subscribe = function() {
    subscriptions.push(self.urgency.subscribe(_.debounce(function(value) {
      ajax
        .command("change-urgency", {
          id: self.applicationId,
          urgency: value})
        .success(function() {
          self.indicator('urgency');
        })
        .call();
    }, 500)));

    subscriptions.push(self.authorityNotice.subscribe(_.debounce(function(value) {
      ajax
        .command("add-authority-notice", {
          id: self.applicationId,
          authorityNotice: value})
        .success(function() {
          self.indicator('notice');
        })
        .call();
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
    subscribe();
  };
};
