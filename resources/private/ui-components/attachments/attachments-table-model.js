LUPAPISTE.AttachmentsTableModel = function( params ) {
  "use strict";
  function stateIcons( data ) {
    return params.isNotNeeded( data )
      ? []
      : _( [[params.isApproved, "lupicon-circle-check positive"],
            [params.isRejected, "lupicon-circle-attention negative"],
            [_.partialRight( _.get, "signed.0"), "lupicon-circle-pen positive"]])
      .map( function( xs ) {
        return _.first( xs )( data ) ? _.last( xs ) : false;
      })
      .filter()
      .value();

  }

  function hasFile(data) {
    return _.get(ko.utils.unwrapObservable(data), "latestVersion.fileId");
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
    }
  };
};
