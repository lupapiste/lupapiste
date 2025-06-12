LUPAPISTE.RepliesService = function() {
  "use strict";
  var self = this;

  self.createNewReply = function(issueId, issueType, applicationId) {
    pageutil.openPage("application", [
      applicationId,
      "process/createReply",
      issueType,
      issueId,
    ]);
  };

  self.goToReplies = function(applicationId) {
    pageutil.openPage("application", [applicationId, "process"]);
  };

  var appId = null;
  var replyStatuses = ko.observable( {} );
  var initTs = 0;

  var fiveMinMs = 5 * 60 * 1000;

  self.clearStatuses = function()  {
    replyStatuses( {} );
  };

  function statusPrep( applicationId ) {
    if( appId !== applicationId
        || Date.now() - initTs > fiveMinMs ) {
      appId = applicationId;
      replyStatuses( {} );
      initTs = Date.now();
    }
  }

  function setStatus( issueId, status ) {
    // We clone the status object in order to make sure that the
    // changes are propagated to the listeners.
    var m = _.clone( replyStatuses() );
    replyStatuses( _.set( m, issueId, status ));
  }

  function fetchStatus( applicationId, issueId ) {
    setStatus( issueId, "fetching" );
    ajax.query("issue-reply-state", {
      id: applicationId,
      issueId: issueId})
      .success(function(data) {
        if( appId === applicationId ) {
          setStatus( issueId, data.state );
        }
      })
      .call();
  }

  self.getReplyStatus = function(issueId, applicationId) {
    statusPrep( applicationId );
    var status = _.get( replyStatuses(), issueId, null );
    if( !status ) {
      fetchStatus( applicationId, issueId );
    }
    return status === "fetching" ? null : status;
  };
};
