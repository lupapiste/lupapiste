var locationSearch = (function() {
  "use strict";

  var searchPointByAddress = function(requestContext, address, onSuccess, onFail, processing) {
    ajax
      .get("/proxy/get-address")
      .param("query", address)
      .processing(processing || _.noop)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchPointByPropertyId = function(requestContext, propertyId, onSuccess, onFail, processing) {
    ajax
      .get("/proxy/point-by-property-id")
      .param("property-id", util.prop.toDbFormat(propertyId))
      .processing(processing || _.noop)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchPropertyId = function(requestContext, x, y, onSuccess, onFail, processing) {
    if (x > 0 && y > 0 ) {
      ajax
      .get ("/proxy/property-id-by-point")
      .param ("x", x)
      .param ("y", y)
      .processing(processing || _.noop)
      .success (requestContext.onResponse(onSuccess))
      .fail (requestContext.onResponse(onFail))
      .call ();
    }
  };

  var searchAddress = function(requestContext, x, y, onSuccess, onFail, processing) {
    if (x > 0 && y > 0) {
      ajax
        .get("/proxy/address-by-point")
        .param("x", x)
        .param("y", y)
        .param("lang", loc.getCurrentLanguage())
        .processing(processing || _.noop)
        .success(requestContext.onResponse(onSuccess))
        .fail(requestContext.onResponse(onFail))
        .call();
    }
  };

  var searchOwnersByPropertyId = function(requestContext, propertyId, onSuccess, onFail, processing) {
      ajax
      .query("owners")
      .param("propertyId", propertyId)
      .processing(processing || _.noop)
      .success(requestContext.onResponse(onSuccess))
      .error(requestContext.onResponse(onFail))
      .call();
  };

  return {
    pointByAddress: searchPointByAddress,
    pointByPropertyId: searchPointByPropertyId,
    propertyIdByPoint: searchPropertyId,
    addressByPoint: searchAddress,
    ownersByPropertyId: searchOwnersByPropertyId
  };
})();
