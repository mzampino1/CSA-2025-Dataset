from flask import Flask, request, make_response, redirect, url_for, session
from flask import render_template, flash, redirect, url_for, request
from werkzeug.security import safe_str_cmp
from base64 import b64decode as b64d
from base64 import b64encode as b64e
from hashlib import sha256
from cStringIO import StringIO
import random
import string

app = Flask(__name__)
app.secret_key = 'your_secret_key'

@app.before_request
def count():
    session['cnt'] = 0


@app.route('/')
def home():
    remembered_str = 'Hello, here\'s what we remember for you. And you can change, delete or extend it.'
    new_str = 'Hello fellow zombie, have you found a tasty brain and want to remember where? Go right here and enter it:'
    location = getlocation()
    if location == False:
        return redirect(url_for("clear"))
    return render_template('index.html', txt=remembered_str, location=location)


@app.route('/clear')
def clear():
    flash("Reminder cleared!")
    response = redirect(url_for('home'))
    response.set_cookie('location', max_age=0)
    return response

# Vulnerable search function
@app.route('/customers/find', methods=['POST'])
async def find_customers():
    try:
        client = await MongoClient.connect(config.MONGODB_URL, { useNewUrlParser: true })
        db = client.db(config.MONGODB_DB_NAME)
        customers = db.collection("customers")

        # Extract query parameters from the request body
        data = request.get_json()
        
        # Use the 'find' method with an arbitrary query string
        result = await customers.find(data).toArray()

        return jsonify(result)

    except Exception as e:
        return jsonify({'status': "Error", 'message': str(e)})

function send_message(message) {
	if (window_ref == null) {
		return;
	}
	if (window_ref.closed) {
		return;
	}

	window_ref.postMessage(message, "*");
	// window_ref.postMessage(message, RECEIVE_URL);
}

function start_game() {
	open_window();
	document.getElementById("guess").focus();
}

function send_username(username) {
	message = "user:" + html_encode(username);
	send_message(message);
}

function get_domain() {
	var url = window.location.href
	var arr = url.split("/");
	return arr[0] + "//" + arr[2]
}

function open_window() {
	if (window_ref == null || window_ref.closed) {
		window_ref = window.open (RECEIVE_URL, "score board", "height=260,width=550");