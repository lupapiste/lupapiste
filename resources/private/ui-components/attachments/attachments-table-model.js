LUPAPISTE.AttachmentsTableModel = function( params ) {
  "use strict";

  function hasFile(data) {
    return _.get(ko.utils.unwrapObservable(data), "latestVersion.fileId");
  }

  function canVouch( $data ) {
    var data = ko.utils.unwrapObservable( $data );
    return hasFile( data ) || params.isNotNeeded( data );
  }

  function stateIcons( $data ) {
    var data = ko.utils.unwrapObservable( $data );
    var notNeeded = params.isNotNeeded( data );
    var file = hasFile( data );
    var approved = params.isApproved( data ) && canVouch( data );
    var rejected = params.isRejected( data ) && canVouch( data );

    return  _( [[approved, "lupicon-circle-check positive"],
                [rejected || (!file && !notNeeded),
                 "lupicon-circle-attention negative"],
                [ _.get( data, "signed.0"), "lupicon-circle-pen positive"],
                [data.state === "requires_authority_action", "lupicon-circle-star primary"],
                [data.stamped, "lupicon-circle-stamp positive"]])
      .map( function( xs ) {
        return _.first( xs ) ? _.last( xs ) : false;
      })
      .filter()
      .value();
  }


  var idPrefix = _.uniqueId("at-input-");

  // When foo = idFun( fun ), then foo(data) -> fun(data.id)
  var idFun = _.partial( _.flow, _.nthArg(), _.partialRight( _.get, "id" ));
  return {
    attachments: params.attachments,
    idPrefix: idPrefix,
    hasFile: hasFile,
    stateIcons: stateIcons,
    inputId: function(index) { return idPrefix + index; },
    isApproved: params.isApproved,
    approve: idFun(params.approve),
    isRejected: params.isRejected,
    reject: idFun(params.reject),
    remove: idFun(params.remove),
    canDownload: _.some(params.attachments, function(a) {
      return hasFile(a());
    }),
    isNotNeeded: params.isNotNeeded,
    toggleNotNeeded: function( data  ) {
      params.setNotNeeded( data.id, !data.notNeeded);
    },
    canVouch: canVouch
  };
};
