// Shared service for desired application handling times for each of the user's organizations
// Handling times are optional and are how many days an app should stay in the submitted state
LUPAPISTE.OrganizationsHandlingTimesService = function() {
  "use strict";
  var self = this;

  self.organizationsHandlingTimes = ko.observable({});

  self.isInitialized = ko.observable(false);

  // Returns true if any organization the user is an authority in has desired application handling times
  self.userHasHandlingTimes = ko.computed(function() {
    return !_.isEmpty(self.organizationsHandlingTimes());
  });

  // Returns the time left for handling the application today (negative if already overtime)
  self.getTimeLeft = function(orgId, submittedTs) {
    var handlingTime = _.get(self.organizationsHandlingTimes(), orgId, null);
    if (!_.isNil(handlingTime)) {
      var handlingTimeLimit = handlingTime.enabled && handlingTime.days;
      if (_.isInteger(handlingTimeLimit) && _.isInteger(submittedTs)) {
        var now = moment().startOf("day").utc();
        var submitted = moment(submittedTs).startOf("day").utc();
        return handlingTimeLimit - now.diff(submitted, "days");
      } else {
        return null;
      }
    }
  };

  ko.computed(function() {
    var authModel = lupapisteApp.models.globalAuthModel;
    if( !self.isInitialized() && authModel.isInitialized() ) {
      if(authModel.ok("users-organizations-handling-times")) {
        ajax.query("users-organizations-handling-times")
          .success(function(res) {
            self.organizationsHandlingTimes(res["handling-times"]);
            self.isInitialized(true);
          })
          .call();
        return true;
      } else {
        self.isInitialized(true);
      }
    }
  });
};
