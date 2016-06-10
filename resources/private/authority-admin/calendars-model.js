LUPAPISTE.AuthAdminCalendarsModel = function () {
  "use strict";

  var self = this;
  
  self.items = ko.observableArray();
  self.initialized = false;
  self.calendarInView = ko.observable();

  function setEnabled(user, value) {
    if (self.initialized) {
      ajax.command("set-calendar-enabled-for-authority", {userId: user.id, enabled: value})
        .success(function(response) {
          util.showSavedIndicator(response);
          user.calendarId(response.calendarId);
        })
        .call();
    }
  }

  self.init = function(data) {
    var users = _.map(data,
      function(user) {
        var calendarEnabledObservable = ko.observable(_.has(user, "calendarId"));
        var calendarIdForLink = ko.observable(user.calendarId || "");
        user = _.extend(user, { calendarEnabled: calendarEnabledObservable,
                                calendarId: calendarIdForLink });
        user.calendarEnabled.subscribe(_.partial(setEnabled, user));
        return user;
      });
    self.items(users || []);
    self.initialized = true;
  };

  var _init = hub.subscribe("calendarService::organizationCalendarsFetched", function(event) {
    self.init(event.calendars);
  });

  var _calendarFetched = hub.subscribe("calendarService::calendarFetched", function(event) {
    self.calendarInView(event.calendar);
  });

  self.dispose = function() {
    hub.unsubscribe(_init);
    hub.unsubscribe(_calendarFetched);
  };
};