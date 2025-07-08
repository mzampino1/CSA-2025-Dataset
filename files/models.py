// Example 1
window.addEventListener('message', writeMessage, false);
function writeMessage(event) {
    document.getElementById("message").innerHTML = event.data;
}